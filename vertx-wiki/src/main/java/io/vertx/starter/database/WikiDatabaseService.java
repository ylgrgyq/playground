package io.vertx.starter.database;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;

@ProxyGen
@VertxGen
public interface WikiDatabaseService {
  @Fluent
  WikiDatabaseService getIndex(Handler<AsyncResult<JsonArray>> resultHandler);

  @Fluent
  WikiDatabaseService getPage(String pageName, Handler<AsyncResult<JsonObject>> resultHandler);

  @Fluent
  WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

  @Fluent
  WikiDatabaseService updatePage(String id, String markdown, Handler<AsyncResult<Void>> resultHandler);

  @Fluent
  WikiDatabaseService deletePage(String id, Handler<AsyncResult<Void>> resultHandler);

  @GenIgnore
  static WikiDatabaseService create(JDBCClient client, HashMap<SqlQuery, String> queriesMap, Handler<AsyncResult<WikiDatabaseService>> resultHandler) {
    return new WikiDatabaseServiceImpl(client, queriesMap, resultHandler);
  }

  @GenIgnore
  static WikiDatabaseService createProxy(Vertx vertx, String address) {
    return new WikiDatabaseServiceVertxEBProxy(vertx, address);
  }

}
