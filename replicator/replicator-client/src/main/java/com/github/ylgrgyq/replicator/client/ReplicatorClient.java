package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.proto.ReplicatorCommand;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ReplicatorClient {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorClient.class);

    private StateMachine stateMachine;
    private NetClient client;

    public ReplicatorClient(StateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public void subscribe(String topic) {
        Subscriber subscriber = new Subscriber(new StateMachine() {
            @Override
            public void apply(List<byte[]> logs) {
                List<String> logsInStr = logs.stream().map(bs -> new String(bs, StandardCharsets.UTF_8)).collect(Collectors.toList());
                logger.info("apply %s", logsInStr);
            }

            @Override
            public void snapshot(byte[] snapshot) {
                logger.info("apply snapshot %s", new String(snapshot, StandardCharsets.UTF_8));
            }
        });

        subscriber.subscribe(topic, client);
    }

    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();

        ChannelFuture future = new Bootstrap()
                .group(group)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new SimpleChannelInboundHandler<byte[]>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext, byte[] o) throws Exception {

                            }
                        });
                    }
                })
                .connect("127.0.0.1", 8888);



    }


}
