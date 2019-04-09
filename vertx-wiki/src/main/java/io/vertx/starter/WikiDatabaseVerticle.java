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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class WikiDatabaseVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n" +
      "\n" +
      "Feel-free to write in Markdown!\n";

  private static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
  private static final String CONFIG_DB_URL = "wikidb.jdbc.url";
  private static final String CONFIG_DB_DRIVER_CLASS = "wikidb.jdbc.driver.class";
  private static final String CONFIG_DB_MAX_POOL_SIZE = "wikidb.jdbc.max.pool.size";
  private static final String CONFIG_DB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sql.queries";

  private String wikidbQueue;
  private JDBCClient jdbcClient;
  private HashMap<SqlQuery, String> queriesMap;

  private enum SqlQuery {
    SQL_CREATE_PAGES_TABLE,
    SQL_GET_PAGE,
    SQL_CREATE_PAGE,
    SQL_SAVE_PAGE,
    SQL_ALL_PAGES,
    SQL_DELETE_PAGE,
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    loadSqlQueries();
    wikidbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

    jdbcClient = JDBCClient.createShared(vertx,
      new JsonObject()
        .put("url", config().getString(CONFIG_DB_URL, "jdbc:hsqldb:file:db/wiki"))
        .put("driver_class", config().getString(CONFIG_DB_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
        .put("max_pool_size", config().getInteger(CONFIG_DB_MAX_POOL_SIZE, 30))
    );

    jdbcClient.getConnection(result -> {
      if (result.failed()) {
        logger.error("Cloud not open a connection to database", result.cause());
        startFuture.fail(result.cause());
      } else {
        SQLConnection conn = result.result();
        conn.execute(queriesMap.get(SqlQuery.SQL_CREATE_PAGES_TABLE), createRet -> {
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

  private void loadSqlQueries() throws IOException {
    String queriesFile = config().getString(CONFIG_DB_SQL_QUERIES_RESOURCE_FILE);
    InputStream input;
    if (queriesFile != null) {
      input = new FileInputStream(queriesFile);
    } else {
      input = getClass().getResourceAsStream("/db-queries.properties");
    }

    Properties props = new Properties();
    props.load(input);
    input.close();
    queriesMap = new HashMap<>();

    queriesMap.put(SqlQuery.SQL_CREATE_PAGES_TABLE, props.getProperty("create-page-table"));
    queriesMap.put(SqlQuery.SQL_ALL_PAGES, props.getProperty("get-index"));
    queriesMap.put(SqlQuery.SQL_CREATE_PAGE, props.getProperty("create-page"));
    queriesMap.put(SqlQuery.SQL_GET_PAGE, props.getProperty("get-page"));
    queriesMap.put(SqlQuery.SQL_SAVE_PAGE, props.getProperty("save-page"));
    queriesMap.put(SqlQuery.SQL_DELETE_PAGE, props.getProperty("delete-page"));
  }

  private void onMessage(Message<JsonObject> req) {
    String action = req.headers().get("action");
    if (action == null) {
      logger.error("No action header for msg with headers {} and body {}", req.headers(), req.body().encodePrettily());
      req.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header for msg");
      return;
    }

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
      default:
        req.fail(ErrorCodes.BAD_ACTION.ordinal(), "Unknown action: " + action);
    }
  }

  private void getIndex(Message<JsonObject> req) {
    jdbcClient.query(queriesMap.get(SqlQuery.SQL_ALL_PAGES), res -> {
      if (res.succeeded()) {
        List<String> pages = res.result()
          .getResults()
          .stream()
          .map(json -> json.getString(0))
          .sorted()
          .collect(Collectors.toList());

        req.reply(new JsonObject().put("pages", new JsonArray(pages)));
      } else {
        logger.error("Get index from database failed", res.cause());
        req.fail(ErrorCodes.DB_ERROR.ordinal(), "Get index from database failed");
      }
    });
  }

  private void getPage(Message<JsonObject> req) {
    String page = req.body().getString("pageName");
    jdbcClient.querySingleWithParams(queriesMap.get(SqlQuery.SQL_GET_PAGE), new JsonArray().add(page), res -> {
      if (res.succeeded()) {
        JsonArray row = res.result();
        boolean newPage = false;
        if (row == null) {
          row = new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN);
          newPage = true;
        }

        Integer id = row.getInteger(0);
        String rawContent = row.getString(1);
        JsonObject replyMsg = new JsonObject();
        replyMsg.put("id", id);
        replyMsg.put("rawContent", rawContent);
        replyMsg.put("isNewPage", newPage);

        req.reply(replyMsg);
      } else {
        logger.error("Get page with name {} from database failed", page, res.cause());
        req.fail(ErrorCodes.DB_ERROR.ordinal(), "Get page from database failed");
      }
    });
  }

  private void updatePage(Message<JsonObject> req) {
    String title = req.body().getString("title");
    String id = req.body().getString("id");
    String markdown = req.body().getString("markdown");
    boolean newPage = req.body().getBoolean("isNewPage");

    String sql;
    JsonArray params = new JsonArray();
    if (newPage) {
      sql = queriesMap.get(SqlQuery.SQL_CREATE_PAGE);
      params.add(title).add(markdown);
    } else {
      sql = queriesMap.get(SqlQuery.SQL_SAVE_PAGE);
      params.add(markdown).add(id);
    }
    jdbcClient.updateWithParams(sql, params, res -> {
      if (res.succeeded()) {
        req.reply("ok");
      } else {
        logger.error("update page failed", res.cause());
        req.fail(ErrorCodes.DB_ERROR.ordinal(), "update page failed");
      }
    });
  }

  private void deletePage(Message<JsonObject> req) {
    String id = req.body().getString("id");

    jdbcClient.updateWithParams(queriesMap.get(SqlQuery.SQL_DELETE_PAGE), new JsonArray().add(id), res -> {
      if (res.succeeded()) {
        req.reply("ok");
      } else {
        logger.error("delete page with id {} from database failed", id, res.cause());
        req.fail(ErrorCodes.DB_ERROR.ordinal(), "delete page from database failed");
      }
    });

  }

  public enum ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
  }
}
