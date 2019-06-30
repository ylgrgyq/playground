package com.github.ylgrgyq.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {

        NetServerOptions options = new NetServerOptions();
        options.setHost("8888");
        NetServer server = vertx.createNetServer(options);
        server.listen(8888, ret -> {
            if (ret.succeeded()){
                startFuture.complete();
            } else {
                startFuture.fail(ret.cause());
            }
        });



        Future<String> deploy = Future.future();
//        vertx.deployVerticle(new WikiDatabaseVerticle(), deploy);

        deploy.compose(id -> {
            Future<String> httpDeploy = Future.future();
            vertx.deployVerticle("io.vertx.starter.http.HttpServiceVerticle",
                    new DeploymentOptions().setInstances(8),
                    httpDeploy);
            return httpDeploy;
        }).setHandler(result -> {
                    if (result.succeeded()) {
                        startFuture.complete();
                    } else {
                        startFuture.fail(result.cause());
                    }
                }
        );
    }
}
