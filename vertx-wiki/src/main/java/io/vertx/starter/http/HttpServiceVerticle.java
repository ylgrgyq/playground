package io.vertx.starter.http;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.starter.database.WikiDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;

public class HttpServiceVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(HttpServiceVerticle.class);
  private static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  private static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private FreeMarkerTemplateEngine templateEngine;
  private WikiDatabaseService dbService;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);

    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);

    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    int port = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8083);
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(port, result -> {
        if (result.succeeded()) {
          logger.info("http server started on {}", port);
          startFuture.complete();
        } else {
          logger.error("start http server failed", result.cause());
          startFuture.fail(result.cause());
        }
      });
  }

  private void indexHandler(RoutingContext context) {
    dbService.getIndex(reply -> {
      if (reply.succeeded()) {
        context.put("title", "Wiki Home");
        context.put("pages", reply.result().getList());

        templateEngine.render(context.data(), "templates/index.ftl", renderResult -> {
          if (renderResult.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(renderResult.result());
          } else {
            logger.error("Render index page failed", renderResult.cause());
            context.fail(500);
          }
        });
      } else {
        logger.error("Get index from database failed", reply.cause());
        context.fail(500);
      }
    });
  }

  private void pageRenderingHandler(RoutingContext context) {
    final String page = context.request().getParam("page");
    dbService.getPage(page, reply -> {
      if (reply.succeeded()) {
        JsonObject pageData = reply.result();
        Integer id = pageData.getInteger("id");
        String rawContent = pageData.getString("rawContent");
        boolean newPage = pageData.getBoolean("isNewPage");

        context.put("title", page);
        context.put("id", id);
        context.put("newPage", newPage ? "yes" : "no");
        context.put("rawContent", rawContent);
        context.put("content", Processor.process(rawContent));
        context.put("timestamp", ZonedDateTime.now().toString());

        templateEngine.render(context.data(), "templates/page.ftl", renderResult -> {
          if (renderResult.succeeded()) {
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(renderResult.result());
          } else {
            logger.error("Render page {} failed", page, reply.cause());
            context.fail(500);
          }
        });

      } else {
        logger.error("Get page {} from database failed", page, reply.cause());
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

    Handler<AsyncResult<Void>> handler = event -> {
      if (event.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      } else {
        logger.error("Get index from database failed", event.cause());
        context.fail(500);
      }
    };

    if (newPage) {
      dbService.createPage(title, markdown, handler);
    } else {
      dbService.updatePage(id, markdown, handler);
    }
  }

  private void pageDeletionHandler(RoutingContext context) {
    String id = context.request().getParam("id");

    dbService.deletePage(id, reply -> {
      if (reply.succeeded()) {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/");
        context.response().end();
      } else {
        logger.error("Get index from database failed", reply.cause());
        context.fail(500);
      }
    });
  }
}
