package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.github.ylgrgyq.replicator.server.Preconditions.checkArgument;

public final class ReplicatorServerOptions {
    private final int port;
    private final String host;
    private final EventLoopGroup bossEventLoopGroup;
    private final EventLoopGroup workerEventLoopGroup;
    private final boolean shouldShutdownBossEventLoopGroup;
    private final boolean shouldShutdownWorkerEventLoopGroup;
    private final int connectionReadTimeoutSecs;
    private final StorageOptions storageOptions;

    private ReplicatorServerOptions(ReplicatorServerOptionsBuilder builder) {
        this.port = builder.port;
        this.host = builder.host;
        this.storageOptions = builder.storageOptions;

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
        this.connectionReadTimeoutSecs = builder.connectionReadTimeoutSecs;
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

    public int getConnectionReadTimeoutSecs() {
        return connectionReadTimeoutSecs;
    }

    public StorageOptions getStorageOptions() {
        return storageOptions;
    }

    public static ReplicatorServerOptionsBuilder builder() {
        return new ReplicatorServerOptionsBuilder();
    }

    public static class ReplicatorServerOptionsBuilder {
        private int port;
        private String host = "localhost";
        private StorageOptions storageOptions;
        private EventLoopGroup bossEventLoopGroup;
        private boolean shouldShutdownBossEventLoopGroup = true;
        private EventLoopGroup workerEventLoopGroup;
        private boolean shouldShutdownWorkerEventLoopGroup = true;
        private boolean preferEpoll;
        private int connectionReadTimeoutSecs = 30;

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

        public ReplicatorServerOptionsBuilder setStorageOptions(StorageOptions options) {
            Objects.requireNonNull(options);

            this.storageOptions = options;
            return this;
        }

        public ReplicatorServerOptionsBuilder setBossEventLoopGroup(EventLoopGroup eventLoopGroup, boolean shouldShutdownEventLoopGroup) {
            Objects.requireNonNull(eventLoopGroup);

            this.bossEventLoopGroup = eventLoopGroup;
            this.shouldShutdownBossEventLoopGroup = shouldShutdownEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setWorkerEventLoopGroup(EventLoopGroup eventLoopGroup, boolean shouldShutdownEventLoopGroup) {
            Objects.requireNonNull(eventLoopGroup);

            this.workerEventLoopGroup = eventLoopGroup;
            this.shouldShutdownWorkerEventLoopGroup = shouldShutdownEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setPreferEpoll(boolean preferEpoll) {
            this.preferEpoll = preferEpoll;
            return this;
        }

        public ReplicatorServerOptionsBuilder setConnectionReadTimeoutSecs(long connectionReadTimeout, TimeUnit unit) {
            Preconditions.checkArgument(connectionReadTimeout > 0);

            this.connectionReadTimeoutSecs = (int)unit.toSeconds(connectionReadTimeout);
            return this;
        }

        public ReplicatorServerOptions build() {
            checkArgument(storageOptions != null, "Please provide storage options");
            checkArgument(port > 0, "Please provide port for Replicator Server to bind");
            checkArgument(host != null, "Please provide host for Replicator Server to bind");

            return new ReplicatorServerOptions(this);
        }
    }
}
