package com.github.ylgrgyq.replicator.server;

import com.github.ylgrgyq.replicator.server.storage.StorageOptions;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
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
    private final ScheduledExecutorService workerScheduledExecutor;
    private final boolean shouldShutdownWorkerScheduledExecutor;

    private ReplicatorServerOptions(ReplicatorServerOptionsBuilder builder) {
        this.port = builder.port;
        this.host = builder.host == null ? "0.0.0.0" : builder.host;
        this.storageOptions = builder.storageOptions;

        if (builder.bossEventLoopGroup == null) {
            this.bossEventLoopGroup = createEventLoopGroup(1, false);
            this.shouldShutdownBossEventLoopGroup = true;
        } else {
            this.bossEventLoopGroup = builder.bossEventLoopGroup;
            this.shouldShutdownBossEventLoopGroup = builder.shouldShutdownBossEventLoopGroup;
        }

        if (builder.workerEventLoopGroup == null) {
            this.workerEventLoopGroup = createEventLoopGroup(0, false);
            this.shouldShutdownWorkerEventLoopGroup = true;
        } else {
            this.workerEventLoopGroup = builder.workerEventLoopGroup;
            this.shouldShutdownWorkerEventLoopGroup = builder.shouldShutdownWorkerEventLoopGroup;
        }

        if (builder.connectionReadTimeoutSecs != null) {
            this.connectionReadTimeoutSecs = builder.connectionReadTimeoutSecs;
        } else {
            this.connectionReadTimeoutSecs = 30;
        }

        if (builder.workerScheduledExecutor != null) {
            this.workerScheduledExecutor = builder.workerScheduledExecutor;
            this.shouldShutdownWorkerScheduledExecutor = builder.shouldShutdownWorkerScheduledExecutor;
        } else {
            int cpus = Runtime.getRuntime().availableProcessors();
            int size = cpus * 2 - 1;
            NamedThreadFactory factory = new NamedThreadFactory("ReplicatorServerWorker");
            this.workerScheduledExecutor = new ScheduledThreadPoolExecutor(size, factory);
            this.shouldShutdownWorkerScheduledExecutor = true;
        }
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

    public boolean shouldShutdownBossEventLoopGroup() {
        return shouldShutdownBossEventLoopGroup;
    }

    public EventLoopGroup getWorkerEventLoopGroup() {
        return workerEventLoopGroup;
    }

    public boolean shouldShutdownWorkerEventLoopGroup() {
        return shouldShutdownWorkerEventLoopGroup;
    }

    public int getConnectionReadTimeoutSecs() {
        return connectionReadTimeoutSecs;
    }

    public StorageOptions getStorageOptions() {
        return storageOptions;
    }

    public ScheduledExecutorService getWorkerScheduledExecutor() {
        return workerScheduledExecutor;
    }

    public boolean shouldShutdownWorkerScheduledExecutor() {
        return shouldShutdownWorkerScheduledExecutor;
    }

    public static ReplicatorServerOptionsBuilder builder() {
        return new ReplicatorServerOptionsBuilder();
    }

    public static class ReplicatorServerOptionsBuilder {
        private Integer port;
        private String host;
        private StorageOptions storageOptions;
        private EventLoopGroup bossEventLoopGroup;
        private Boolean shouldShutdownBossEventLoopGroup;
        private EventLoopGroup workerEventLoopGroup;
        private Boolean shouldShutdownWorkerEventLoopGroup;
        private Boolean preferEpoll;
        private Integer connectionReadTimeoutSecs;
        private ScheduledExecutorService workerScheduledExecutor;
        private Boolean shouldShutdownWorkerScheduledExecutor;

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

        public ReplicatorServerOptionsBuilder setBossEventLoopGroup(
                EventLoopGroup eventLoopGroup,
                boolean shouldShutdownEventLoopGroup) {
            Objects.requireNonNull(eventLoopGroup);

            this.bossEventLoopGroup = eventLoopGroup;
            this.shouldShutdownBossEventLoopGroup = shouldShutdownEventLoopGroup;
            return this;
        }

        public ReplicatorServerOptionsBuilder setWorkerEventLoopGroup(
                EventLoopGroup eventLoopGroup,
                boolean shouldShutdownEventLoopGroup) {
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
            Objects.requireNonNull(unit);

            this.connectionReadTimeoutSecs = (int) unit.toSeconds(connectionReadTimeout);
            return this;
        }

        public ReplicatorServerOptionsBuilder setWorkerScheduledExecutor(
                ScheduledExecutorService workerScheduledExecutor,
                boolean shouldShutdownWorkerScheduledExecutor) {
            Objects.requireNonNull(workerScheduledExecutor);

            this.workerScheduledExecutor = workerScheduledExecutor;
            this.shouldShutdownWorkerScheduledExecutor = shouldShutdownWorkerScheduledExecutor;
            return this;
        }

        public ReplicatorServerOptions build() {
            checkArgument(storageOptions != null, "Please provide storage options");
            checkArgument(port > 0, "Please provide port for Replicator Server to bind");

            return new ReplicatorServerOptions(this);
        }
    }
}
