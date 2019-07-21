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

        if (! jobQueue.offer(StateMachineJob.newApplySnapshotJob(stateMachine, snapshot, future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINEQUEUEFULL));
        }

        return future;
    }

    public CompletableFuture<Void> applyLogs(List<byte[]> logs) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (! jobQueue.offer(StateMachineJob.newApplyLogsJob(stateMachine, logs, future))) {
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
                        job.run();
                    } catch (Throwable ex) {
                        if (job instanceof StateMachineJob) {
                            try {
                                stateMachine.exceptionCaught(ex);
                            } catch (Exception ex2) {
                                logger.error("got exception when handling state machine exception", ex2);
                            }
                        } else {
                            throw ex;
                        }
                    } finally {
                        job.done();
                    }
                } catch (Exception ex) {
                    logger.error("got unexpected exception while running job");
                }
            }
        }).start();
    }

    public void shutdown() {
        stop = true;
    }

    private static class Job implements Runnable{
        private Runnable job;
        private CompletableFuture<Void> future;

        public Job(Runnable job, CompletableFuture<Void> future) {
            this.job = job;
            this.future = future;
        }

        @Override
        public void run() {
            job.run();
        }

        public void done() {
            future.complete(null);
        }
    }

    private static class StateMachineJob extends Job {
        public StateMachineJob(Runnable job, CompletableFuture<Void> future) {
            super(job, future);
        }

        static StateMachineJob newApplySnapshotJob(StateMachine stateMachine, Snapshot snapshot, CompletableFuture<Void> future){
            return new StateMachineJob(() -> stateMachine.snapshot(snapshot.toByteArray()), future);
        }

        static StateMachineJob newApplyLogsJob(StateMachine stateMachine, List<byte[]> logs, CompletableFuture<Void> future){
            return new StateMachineJob(() -> stateMachine.apply(logs), future);
        }
    }

    private static class ReplicatorClientInternalJob extends Job {
        public ReplicatorClientInternalJob(Runnable job, CompletableFuture<Void> future) {
            super(job, future);
        }
    }
}
