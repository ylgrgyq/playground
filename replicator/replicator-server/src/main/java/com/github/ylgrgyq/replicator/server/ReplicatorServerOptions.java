package com.github.ylgrgyq.replicator.server;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Objects;

import static com.github.ylgrgyq.replicator.server.Preconditions.checkArgument;

public class ReplicatorServerOptions {
    private final int port;
    private final String host;
    private final String storagePath;
    private final EventLoopGroup bossEventLoopGroup;
    private final EventLoopGroup workerEventLoopGroup;
    private final boolean shouldShutdownBossEventLoopGroup;
    private final boolean shouldShutdownWorkerEventLoopGroup;

    private ReplicatorServerOptions(ReplicatorServerOptionsBuilder builder) {
        this.port = builder.port;
        this.host = builder.host;
        this.storagePath = builder.storagePath;

        if (builder.bossEventLoopGroup == null) {
            this.bossEventLoopGroup = createEventLoopGroup(1, builder.preferEpoll);
        } else {
            this.bossEventLoopGroup = builder.bossEventLoopGroup;
        }

        if (builder.workerEventLoopGroup == null) {
            this.workerEventLoopGroup = createEventLoopGroup(0, builder.preferEpoll);
        } else {
            this.workerEventLoopGroup = builder.workerEventLoopGroup;
        }

        this.shouldShutdownBossEventLoopGroup = builder.shouldShutdownBossEventLoopGroup;
        this.shouldShutdownWorkerEventLoopGroup = builder.shouldShutdownWorkerEventLoopGroup;
    }

    private EventLoopGroup createEventLoopGroup(int nThreads, boolean preferEpoll) {
        if (preferEpoll) {
            return new EpollEventLoopGroup(nThreads);
        } else {
            return new NioEventLoopGroup(nThreads);
        }
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public EventLoopGroup getBossEventLoopGroup() {
        return bossEventLoopGroup;
    }

    public boolean isShouldShutdownBossEventLoopGroup() {
        return shouldShutdownBossEventLoopGroup;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public boolean isShouldShutdownWorkerEventLoopGroup() {
        return shouldShutdownWorkerEventLoopGroup;
    }

    public static ReplicatorServerOptionsBuilder builder() {
        return new ReplicatorServerOptionsBuilder();
    }

    public static class ReplicatorServerOptionsBuilder {
        private int port;
        private String host;
        private String storagePath;
        private EventLoopGroup bossEventLoopGroup;
        private boolean shouldShutdownBossEventLoopGroup = true;
        private EventLoopGroup workerEventLoopGroup;
        private boolean shouldShutdownWorkerEventLoopGroup = true;
        private boolean preferEpoll;

        public ReplicatorServerOptionsBuilder setPort(int port) {
            checkArgument(port > 0);

            this.port = port;
            return this;
        }

        public ReplicatorServerOptionsBuilder setHost(String host) {
            Objects.requireNonNull(host);

            this.host = host;
            return this;
        }

        public ReplicatorServerOptionsBuilder setStoragePath(String path) {
            Objects.requireNonNull(path);

            this.storagePath = path;
            return this;
        }

        public ReplicatorServerOptionsBuilder setBossEventLoopGroup(EventLoopGroup bossEventLoopGroup) {
            Objects.requireNonNull(bossEventLoopGroup);

            this.bossEventLoopGroup = bossEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setShouldShutdownBossEventLoopGroup(boolean shouldShutdownBossEventLoopGroup) {
            this.shouldShutdownBossEventLoopGroup = shouldShutdownBossEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setWorkerEventLoopGroup(EventLoopGroup workerEventLoopGroup) {
            Objects.requireNonNull(workerEventLoopGroup);

            this.workerEventLoopGroup = workerEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setShouldShutdownWorkerEventLoopGroup(boolean shouldShutdownWorkerEventLoopGroup) {
            this.shouldShutdownWorkerEventLoopGroup = shouldShutdownWorkerEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setPreferEpoll(boolean preferEpoll) {
            this.preferEpoll = preferEpoll;
            return this;
        }

        public ReplicatorServerOptions build() {
            checkArgument(storagePath != null, "Please provide storage path");
            checkArgument(port > 0, "Please provide port for Replicator Server to bind");
            checkArgument(host != null, "Please provide host for Replicator Server to bind");

            return new ReplicatorServerOptions(this);
        }
    }
}
