package com.github.ylgrgyq.replicator.benchmark.client;

import com.github.ylgrgyq.replicator.client.ReplicatorClient;
import com.github.ylgrgyq.replicator.client.ReplicatorClientOptions;
import com.github.ylgrgyq.replicator.client.StateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ReplicatorBenchmarkClient {
    private static final Logger logger = LoggerFactory.getLogger(ReplicatorBenchmarkClient.class);

    public static void main(String[] args) throws Exception{
        URI uri = new URI("ws://localhost:8888");

        ReplicatorClientOptions options = ReplicatorClientOptions
                .builder()
                .setUri(uri)
                .build();


        logger.info("Start sync log test...");
        long logsCount = 1000000;
        for (int i = 1; i <= 5; ++i) {
            TestingStateMachine stateMachine = new TestingStateMachine(logsCount);
            ReplicatorClient client = new ReplicatorClient("benchmark", stateMachine, options);
            long start = System.nanoTime();
            CompletableFuture<Void> f = client.start();
            f.exceptionally(t -> {
                logger.error("connect to server failed", t);
                return null;
            });

            stateMachine.waitComplete();
            long duration = System.nanoTime() - start;
            logger.info("Test finished for the {} round.", i);
            logger.info("Synced {} logs in {} milliseconds.", logsCount, TimeUnit.NANOSECONDS.toMillis(duration));

            client.shutdown().get();

            Thread.sleep(2000);

            logger.info("The {} round sync log test succeed", i);
        }
    }

    private static class TestingStateMachine implements StateMachine{
        private long counter = 0;
        private CompletableFuture<Void> completeFuture;
        private long expectLogsCount;

        private TestingStateMachine(long expectLogsCount) {
            this.completeFuture = new CompletableFuture<>();
            this.expectLogsCount = expectLogsCount;
        }

        @Override
        public void apply(List<byte[]> logs) {
            counter += logs.size();
            if (counter >= expectLogsCount) {
                completeFuture.complete(null);
            }
        }

        @Override
        public void snapshot(byte[] snapshot) {
            logger.info("apply snapshot {}", new String(snapshot, StandardCharsets.UTF_8));
        }

        @Override
        public void reset() {
            logger.info("reset called");
        }

        void waitComplete() throws InterruptedException{
            try {
                completeFuture.get();
            } catch (ExecutionException ex) {
                // will not happen
            }
        }
    }
}
