package io.vertx.starter.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public class WikiDatabaseVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

  private static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
  private static final String CONFIG_DB_URL = "wikidb.jdbc.url";
  private static final String CONFIG_DB_DRIVER_CLASS = "wikidb.jdbc.driver.class";
  private static final String CONFIG_DB_MAX_POOL_SIZE = "wikidb.jdbc.max.pool.size";
  private static final String CONFIG_DB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sql.queries";

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    HashMap<SqlQuery,String> queriesMap = loadSqlQueries();
    String wikidbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

    JDBCClient jdbcClient = JDBCClient.createShared(vertx,
      new JsonObject()
        .put("url", config().getString(CONFIG_DB_URL, "jdbc:hsqldb:file:db/wiki"))
        .put("driver_class", config().getString(CONFIG_DB_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
        .put("max_pool_size", config().getInteger(CONFIG_DB_MAX_POOL_SIZE, 30))
    );

    WikiDatabaseService.create(jdbcClient, queriesMap, ready -> {
      if (ready.succeeded()) {
        ServiceBinder binder = new ServiceBinder(vertx);
        binder.setAddress(wikidbQueue)
          .register(WikiDatabaseService.class, ready.result());
        startFuture.complete();
      } else {
        startFuture.fail(ready.cause());
      }
    });
  }

  private HashMap<SqlQuery, String> loadSqlQueries() throws IOException {
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
    HashMap<SqlQuery, String> queriesMap = new HashMap<>();

    queriesMap.put(SqlQuery.SQL_CREATE_PAGES_TABLE, props.getProperty("create-page-table"));
    queriesMap.put(SqlQuery.SQL_ALL_PAGES, props.getProperty("get-index"));
    queriesMap.put(SqlQuery.SQL_CREATE_PAGE, props.getProperty("create-page"));
    queriesMap.put(SqlQuery.SQL_GET_PAGE, props.getProperty("get-page"));
    queriesMap.put(SqlQuery.SQL_SAVE_PAGE, props.getProperty("save-page"));
    queriesMap.put(SqlQuery.SQL_DELETE_PAGE, props.getProperty("delete-page"));
    return queriesMap;
  }
}
