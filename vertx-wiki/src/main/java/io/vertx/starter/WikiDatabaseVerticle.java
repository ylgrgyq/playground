package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WikiDatabaseVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(WikiDatabaseVerticle.class);
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n" +
      "\n" +
      "Feel-free to write in Markdown!\n";

  private static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private String wikidbQueue;
  private JDBCClient jdbcClient;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    wikidbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

    jdbcClient = JDBCClient.createShared(vertx,
      new JsonObject()
        .put("url", "jdbc:hsqldb:file:db/wiki")
        .put("driver_class", "org.hsqldb.jdbcDriver")
        .put("max_pool_size", 30)
    );

    jdbcClient.getConnection(result -> {
      if (result.failed()) {
        logger.error("Cloud not open a connection to database", result.cause());
        startFuture.fail(result.cause());
      } else {
        SQLConnection conn = result.result();
        conn.execute(SQL_CREATE_PAGES_TABLE, createRet -> {
          conn.close();
          if (createRet.failed()) {
            logger.error("Create Pages table failed", createRet.cause());
            startFuture.fail(createRet.cause());
          } else {
            vertx.eventBus().consumer(wikidbQueue, this::onMessage);
            startFuture.complete();
          }
        });
      }
    });

  }

  private void onMessage(Message<JsonObject> req) {
    String action = req.headers().get("action");
    switch (action) {
      case "get-index":
        getIndex(req);
        break;
      case "get-page":
        getPage(req);
        break;
      case "update-page":
        updatePage(req);
        break;
      case "delete-page":
        deletePage(req);
        break;
    }
  }

  private void getIndex(Message<JsonObject> req) {
    jdbcClient.getConnection(result -> {
      if (result.succeeded()) {
        SQLConnection conn = result.result();
        conn.query(SQL_ALL_PAGES, res -> {
          conn.close();

          if (res.succeeded()) {
            JsonArray pages = new JsonArray();

            res.result()
              .getResults()
              .stream()
              .map(json -> json.getString(0))
              .sorted()
              .forEach(pages::add);

            req.reply(new JsonObject().put("pages", pages));
          } else {
            logger.error("Get index from database failed", res.cause());
            res.failed();
            req.fail(500, "Get index from database failed");
          }
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        req.fail(500, "Open Connection to Database failed");
      }
    });
  }

  private void getPage(Message<JsonObject> req) {
    String page = req.body().getString("pageName");
    jdbcClient.getConnection(result -> {
      if (result.succeeded()) {
        SQLConnection conn = result.result();
        conn.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), res -> {
          conn.close();
          JsonArray row = res.result()
            .getResults()
            .stream()
            .findFirst()
            .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));

          Integer id = row.getInteger(0);
          String rawContent = row.getString(1);
          boolean newPage = res.result().getResults().isEmpty();
          JsonObject replyMsg = new JsonObject();
          replyMsg.put("id", id);
          replyMsg.put("rawContent", rawContent);
          replyMsg.put("isNewPage", newPage);

          req.reply(replyMsg);
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        req.fail(500, "Open Connection to Database failed");
      }
    });
  }

  private void updatePage(Message<JsonObject> req) {
    String title = req.body().getString("title");
    String id = req.body().getString("id");
    String markdown = req.body().getString("markdown");
    boolean newPage = req.body().getBoolean("isNewPage");

    jdbcClient.getConnection(result -> {
      if (result.succeeded()) {
        SQLConnection conn = result.result();
        String sql;
        JsonArray params = new JsonArray();
        if (newPage) {
          sql = SQL_CREATE_PAGE;
          params.add(title).add(markdown);
        } else {
          sql = SQL_SAVE_PAGE;
          params.add(markdown).add(id);
        }
        conn.queryWithParams(sql, params, res -> {
          conn.close();

          if (res.succeeded()) {
            req.reply("ok");
          } else {
            logger.error("Get index from database failed", res.cause());
            req.fail(500, "Get index from database failed");
          }
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        req.fail(500, "Open Connection to Database failed");
      }
    });
  }

  private void deletePage(Message<JsonObject> req) {
    String id = req.body().getString("id");

    jdbcClient.getConnection(result -> {
      if (result.succeeded()) {
        SQLConnection conn = result.result();
        conn.queryWithParams(SQL_DELETE_PAGE, new JsonArray().add(id), res -> {
          conn.close();

          if (res.succeeded()) {
            req.reply("ok");
          } else {
            logger.error("Get index from database failed", res.cause());
            req.fail(500, "Get index from database failed");
          }
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        req.fail(500, "Open Connection to Database failed");
      }
    });
  }
}
