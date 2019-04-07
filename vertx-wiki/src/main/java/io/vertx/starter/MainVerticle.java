package io.vertx.starter;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

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

  private JDBCClient jdbcClient;
  private FreeMarkerTemplateEngine templateEngine;

  @Override
  public void start(Future<Void> startFuture) {
    templateEngine = FreeMarkerTemplateEngine.create(vertx);
    Future<Void> steps = prepareDatabase().compose(f -> startHttpServer());
    steps.setHandler(result -> {
        if (result.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      }
    );
  }

  private Future<Void> prepareDatabase() {
    Future<Void> future = Future.future();

    jdbcClient = JDBCClient.createShared(vertx,
      new JsonObject()
        .put("url", "jdbc:hsqldb:file:db/wiki")
        .put("driver_class", "org.hsqldb.jdbcDriver")
        .put("max_pool_size", 30)
    );

    jdbcClient.getConnection(result -> {
      if (result.failed()) {
        logger.error("Cloud not open a connection to database", result.cause());
        future.fail(result.cause());
      } else {
        SQLConnection conn = result.result();
        conn.execute(SQL_CREATE_PAGES_TABLE, createRet -> {
          conn.close();
          if (createRet.failed()) {
            logger.error("Create Pages table failed", createRet.cause());
            future.fail(createRet.cause());
          } else {
            future.complete();
          }
        });
      }
    });

    return future;
  }

  private Future<Void> startHttpServer() {
    Future<Void> future = Future.future();

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);

    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
//    router.post("/delete").handler(this::pageDeletionHandler);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8083);

    return future;
  }

  private void indexHandler(RoutingContext context) {
    jdbcClient.getConnection(result -> {
      if (result.succeeded()) {
        SQLConnection conn = result.result();
        conn.query(SQL_ALL_PAGES, res -> {
          conn.close();

          if (res.succeeded()) {
            List<String> pages = res.result()
              .getResults()
              .stream()
              .map(json -> json.getString(0))
              .sorted()
              .collect(Collectors.toList());
            context.put("title", "Wiki Home");
            context.put("pages", pages);

            templateEngine.render(context.data(), "templates/index.ftl", renderResult -> {
              if (renderResult.succeeded()) {
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(renderResult.result());
              } else {
                logger.error("Render index page failed", res.cause());
                context.fail(500);
              }
            });

          } else {
            logger.error("Get index from database failed", res.cause());
            context.fail(500);
          }
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        context.fail(500);
      }
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    String page = context.request().getParam("page");
    jdbcClient.getConnection(result -> {
      if (result.succeeded()) {
        SQLConnection conn = result.result();
        conn.queryWithParams(SQL_GET_PAGE, new JsonArray().add(page), res -> {
          conn.close();

          if (res.succeeded()) {
            JsonArray row = res.result()
              .getResults()
              .stream()
              .findFirst()
              .orElseGet(() -> new JsonArray().add(-1).add(EMPTY_PAGE_MARKDOWN));
            Integer id = row.getInteger(0);
            String rawContent = row.getString(1);

            context.put("title", page);
            context.put("id", id);
            context.put("newPage", res.result().getResults().isEmpty() ? "yes" : "no");
            context.put("rawContent", rawContent);
            context.put("content", Processor.process(rawContent));
            context.put("timestamp", ZonedDateTime.now().toString());

            templateEngine.render(context.data(), "templates/page.ftl", renderResult -> {
              if (renderResult.succeeded()) {
                context.response().putHeader("Content-Type", "text/html");
                context.response().end(renderResult.result());
              } else {
                logger.error("Render index page failed", res.cause());
                context.fail(500);
              }
            });

          } else {
            logger.error("Get index from database failed", res.cause());
            context.fail(500);
          }
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        context.fail(500);
      }
    });
  }

  private void pageCreateHandler(RoutingContext context) {
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }

    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private void pageUpdateHandler(RoutingContext context) {
    String title = context.request().getParam("title");
    String id = context.request().getParam("id");
    String markdown = context.request().getParam("markdown");
    boolean newPage = "yes".equals(context.request().getParam("newPage"));

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
            context.response().setStatusCode(303);
            context.response().putHeader("Location", "/wiki/" + title);
            context.response().end();
          } else {
            logger.error("Get index from database failed", res.cause());
            context.fail(500);
          }
        });
      } else {
        logger.error("Open Connection to Database failed", result.cause());
        context.fail(500);
      }
    });
  }


}
