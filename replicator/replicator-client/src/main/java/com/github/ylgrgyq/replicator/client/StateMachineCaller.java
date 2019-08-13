package com.github.ylgrgyq.replicator.client;

import com.github.ylgrgyq.replicator.common.ReplicatorError;
import com.github.ylgrgyq.replicator.common.entity.Snapshot;
import com.github.ylgrgyq.replicator.common.exception.ReplicatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

final class StateMachineCaller {
    private static final Logger logger = LoggerFactory.getLogger(StateMachineCaller.class);

    private final StateMachine stateMachine;
    private final BlockingQueue<Job> jobQueue;
    private final ReplicatorClient client;
    private CompletableFuture<Void> terminationFuture;
    private volatile boolean stop;

    StateMachineCaller(StateMachine stateMachine, ReplicatorClient client) {
        this.stop = false;
        this.stateMachine = stateMachine;
        this.client = client;
        this.jobQueue = new LinkedBlockingQueue<>();
        Thread worker = new Thread(() -> {
            while (!stop) {
                try {
                    Job job = jobQueue.take();
                    try {
                        job.run();
                        job.success();
                    } catch (Throwable ex) {
                        handleExecuteJobFailed(job, ex);
                    }
                } catch (Exception ex) {
                    logger.error("got unexpected exception while running job");
                }
            }
        });

        worker.setName("State-Machine-Caller-Worker");
        worker.start();
    }

    public CompletableFuture<Void> applySnapshot(Snapshot snapshot) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (stop) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_ALREADY_SHUTDOWN));
        }

        if (!jobQueue.offer(newApplySnapshotJob(snapshot, future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_QUEUE_FULL));
        }

        return future;
    }

    public CompletableFuture<Void> applyLogs(List<byte[]> logs) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (stop) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_ALREADY_SHUTDOWN));
        }

        if (!jobQueue.offer(newApplyLogsJob(logs, future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_QUEUE_FULL));
        }


        return future;
    }

    CompletableFuture<Void> resetStateMachine() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (stop) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_ALREADY_SHUTDOWN));
        }

        if (!jobQueue.offer(newResetStateMachinejob(future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_QUEUE_FULL));
        }

        return future;
    }

    private void handleExecuteJobFailed(Job job, Throwable t) {
        logger.error("process job failed", t);
        stop = true;
        job.fail(new ReplicatorException(ReplicatorError.ESTATEMACHINE_EXECUTION_ERROR, t));
        client.stop().join();
    }

    synchronized CompletableFuture<Void> shutdown() {
        if (terminationFuture != null) {
            return terminationFuture;
        }

        stop = true;
        terminationFuture = new CompletableFuture<>();

        jobQueue.offer(new ReplicatorClientInternalJob(() -> {
            // do nothing. it is only used to wake up worker thread.
        }, terminationFuture));

        return terminationFuture;
    }

    private class Job implements Runnable {
        private Runnable job;
        private CompletableFuture<Void> future;

        Job(Runnable job, CompletableFuture<Void> future) {
            this.job = job;
            this.future = future;
        }

        @Override
        public void run() {
            job.run();
        }

        void fail(ReplicatorException ex) {
            future.completeExceptionally(ex);
        }

        void success() {
            future.complete(null);
        }
    }

    private class StateMachineJob extends Job {
        StateMachineJob(Runnable job, CompletableFuture<Void> future) {
            super(job, future);
        }
    }

    private StateMachineJob newApplySnapshotJob(Snapshot snapshot, CompletableFuture<Void> future) {
        return new StateMachineJob(() -> stateMachine.snapshot(snapshot.serialize()), future);
    }

    private StateMachineJob newApplyLogsJob(List<byte[]> logs, CompletableFuture<Void> future) {
        return new StateMachineJob(() -> stateMachine.apply(logs), future);
    }

    private StateMachineJob newResetStateMachinejob(CompletableFuture<Void> future) {
        return new StateMachineJob(stateMachine::reset, future);
    }

    private class ReplicatorClientInternalJob extends Job {
        ReplicatorClientInternalJob(Runnable job, CompletableFuture<Void> future) {
            super(job, future);
        }
    }
}
