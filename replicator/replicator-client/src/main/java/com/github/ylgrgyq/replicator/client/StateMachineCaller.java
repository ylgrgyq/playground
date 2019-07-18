package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.proto.Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class StateMachineCaller {
    private static final Logger logger = LoggerFactory.getLogger(StateMachineCaller.class);

    private StateMachine stateMachine;
    private BlockingQueue<Job> jobQueue;
    private volatile boolean stop;

    public StateMachineCaller(StateMachine stateMachine) {
        this.stop = false;
        this.stateMachine = stateMachine;
        this.jobQueue = new ArrayBlockingQueue<>(1000);

    }

    public CompletableFuture<Void> applySnapshot(Snapshot snapshot) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Job newJob = new Job(() -> stateMachine.snapshot(snapshot.toByteArray()), future);
        if (! jobQueue.offer(newJob)) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINEQUEUEFULL));
        }

        return future;
    }

    public CompletableFuture<Void> applyLogs(List<byte[]> logs) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Job newJob = new Job(() -> stateMachine.apply(logs), future);
        if (! jobQueue.offer(newJob)) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINEQUEUEFULL));
        }


        return future;
    }

    public void start() {
        new Thread(() -> {
            while (!stop) {
                try {
                    Job job = jobQueue.take();
                    try {
                        job.job.run();
                    } catch (Throwable ex) {
                        try {
                            stateMachine.exceptionCaught(ex);
                        } catch (Exception ex2) {
                            logger.error("got exception when handle state machine exception", ex2);
                        }
                    } finally {
                        job.future.complete(null);
                    }
                } catch (Exception ex) {

                }
            }
        });
    }

    public void shutdown() {
        stop = true;
    }

    private static class Job {
        private Runnable job;
        private CompletableFuture<Void> future;

        public Job(Runnable job, CompletableFuture<Void> future) {
            this.job = job;
            this.future = future;
        }
    }
}
