package com.github.ylgrgyq.replicator.client;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ReplicatorClientOptions {
    private final int port;
    private final String host;
    private final long reconnectDelaySeconds;
    private final URI uri;
    private final int pendingFlushLogsLowWaterMark;
    private final int pingIntervalSec;

    private ReplicatorClientOptions(ReplicatorClientOptionsBuilder builder) {
        this.uri = builder.uri;
        this.host = uri.getHost();
        this.port = uri.getPort();
        this.pendingFlushLogsLowWaterMark = builder.pendingFlushLogsLowWaterMark == null ? 10 : builder.pendingFlushLogsLowWaterMark;
        this.reconnectDelaySeconds = builder.reconnectDelaySeconds == null ? 10 : builder.reconnectDelaySeconds;
        this.pingIntervalSec = builder.pingIntervalSec == null ? 10 : builder.pingIntervalSec;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public long getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public URI getUri() {
        return uri;
    }

    public int getPendingFlushLogsLowWaterMark() {
        return pendingFlushLogsLowWaterMark;
    }

    public int getPingIntervalSec() {
        return pingIntervalSec;
    }

    public static ReplicatorClientOptionsBuilder builder() {
        return new ReplicatorClientOptionsBuilder();
    }

    public static class ReplicatorClientOptionsBuilder {
        private Long reconnectDelaySeconds;
        private URI uri;
        private Integer pendingFlushLogsLowWaterMark;
        private Integer pingIntervalSec;

        public ReplicatorClientOptionsBuilder setReconnectDelaySeconds(long reconnectDelay, TimeUnit unit) {
            Preconditions.checkArgument(reconnectDelay > 0);
            Objects.requireNonNull(unit);

            this.reconnectDelaySeconds = unit.toSeconds(reconnectDelay);
            return this;
        }

        public ReplicatorClientOptionsBuilder setUri(URI uri) {
            this.uri = uri;
            return this;
        }

        public ReplicatorClientOptionsBuilder setPendingFlushLogsLowWaterMark(int pendingFlushLogsLowWaterMark) {
            Preconditions.checkArgument(pendingFlushLogsLowWaterMark > 0);

            this.pendingFlushLogsLowWaterMark = pendingFlushLogsLowWaterMark;
            return this;
        }

        public ReplicatorClientOptionsBuilder setPingIntervalSec(long pingInterval, TimeUnit unit) {
            Preconditions.checkArgument(pingInterval > 0);
            Objects.requireNonNull(unit);

            this.pingIntervalSec = (int)unit.toSeconds(pingInterval);
            return this;
        }

        public ReplicatorClientOptions build() {
            Preconditions.checkArgument(uri == null, "Please set target server URI");

            return new ReplicatorClientOptions(this);
        }
    }
}
