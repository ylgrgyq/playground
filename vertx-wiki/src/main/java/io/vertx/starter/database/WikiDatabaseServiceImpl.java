package io.vertx.starter.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class WikiDatabaseServiceImpl implements WikiDatabaseService {
  private static final Logger logger = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);

  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n" +
      "\n" +
      "Feel-free to write in Markdown!\n";

  private final HashMap<SqlQuery, String> queriesMap;
  private final JDBCClient jdbcClient;

  WikiDatabaseServiceImpl(JDBCClient client, HashMap<SqlQuery, String> queriesMap, Handler<AsyncResult<WikiDatabaseService>> resultHandler) {
    this.jdbcClient = client;
    this.queriesMap = queriesMap;

    this.jdbcClient.getConnection(result -> {
      if (result.failed()) {
        logger.error("Cloud not open a connection to database", result.cause());
        resultHandler.handle(Future.failedFuture(result.cause()));
      } else {
        SQLConnection conn = result.result();
        conn.execute(this.queriesMap.get(SqlQuery.SQL_CREATE_PAGES_TABLE), createRet -> {
          conn.close();
          if (createRet.failed()) {
            logger.error("Create Pages table failed", createRet.cause());
            resultHandler.handle(Future.failedFuture(createRet.cause()));
          } else {
            resultHandler.handle(Future.succeededFuture(this));
          }
        });
      }
    });
  }

  @Override
  public WikiDatabaseService getIndex(Handler<AsyncResult<JsonArray>> resultHandler) {
    jdbcClient.query(queriesMap.get(SqlQuery.SQL_ALL_PAGES), res -> {
      if (res.succeeded()) {
        List<String> pages = res.result()
          .getResults()
          .stream()
          .map(json -> json.getString(0))
          .sorted()
          .collect(Collectors.toList());

        resultHandler.handle(Future.succeededFuture(new JsonArray(pages)));
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiDatabaseService getPage(String pageName, Handler<AsyncResult<JsonObject>> resultHandler) {
    jdbcClient.querySingleWithParams(queriesMap.get(SqlQuery.SQL_GET_PAGE), new JsonArray().add(pageName), res -> {
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

        resultHandler.handle(Future.succeededFuture(replyMsg));
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    String sql = queriesMap.get(SqlQuery.SQL_CREATE_PAGE);
    JsonArray params = new JsonArray();
    params.add(title).add(markdown);

    jdbcClient.updateWithParams(sql, params, res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });

    return this;
  }

  @Override
  public WikiDatabaseService updatePage(String id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    String sql = queriesMap.get(SqlQuery.SQL_SAVE_PAGE);
    JsonArray params = new JsonArray();
    params.add(markdown).add(id);

    jdbcClient.updateWithParams(sql, params, res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });
    return this;
  }

  @Override
  public WikiDatabaseService deletePage(String id, Handler<AsyncResult<Void>> resultHandler) {
    jdbcClient.updateWithParams(queriesMap.get(SqlQuery.SQL_DELETE_PAGE), new JsonArray().add(id), res -> {
      if (res.succeeded()) {
        resultHandler.handle(Future.succeededFuture());
      } else {
        resultHandler.handle(Future.failedFuture(res.cause()));
      }
    });

    return this;
  }
}
