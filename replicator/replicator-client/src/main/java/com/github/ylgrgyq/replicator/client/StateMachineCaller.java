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

    private final StateMachine stateMachine;
    private final BlockingQueue<Job> jobQueue;
    private final ReplicatorClient client;
    private volatile boolean stop;
    private CompletableFuture<Void> terminationFuture;
    private Job shutdownJob;


    StateMachineCaller(StateMachine stateMachine, ReplicatorClient client) {
        this.stop = false;
        this.stateMachine = stateMachine;
        this.client = client;
        this.jobQueue = new ArrayBlockingQueue<>(1000);

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

    CompletableFuture<Void> resetStateMachine(){
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (stop) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_ALREADY_SHUTDOWN));
        }

        if (!jobQueue.offer(newResetStateMachinejob(future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_QUEUE_FULL));
        }

        return future;
    }

    void start() {
        Thread woker = new Thread(() -> {
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

        woker.setName("State-Machine-Caller-Worker");
        woker.start();
    }

    private void handleExecuteJobFailed(Job job, Throwable t) {
        logger.error("process job failed", t);
        shutdown();
        job.fail(new ReplicatorException(ReplicatorError.ESTATEMACHINE_EXECUTION_ERROR, t));
        while ((job = jobQueue.poll()) != null) {
            if (job != shutdownJob) {
                job.fail(new ReplicatorException(ReplicatorError.ECLIENT_ALREADY_SHUTDOWN));
            } else {
                job.success();
                assert jobQueue.isEmpty();
            }
        }
        client.shutdown();
    }

    synchronized CompletableFuture<Void> shutdown() {
        if (stop) {
            return terminationFuture;
        }

        terminationFuture = new CompletableFuture<>();

        stop = true;

        shutdownJob = new ReplicatorClientInternalJob(() -> {
            // do nothing. Because it is only used as a mark which
            // indicate every job before is executed. So we can
            // shutdown safely.
        }, terminationFuture);

        if (!jobQueue.offer(shutdownJob)) {
            Thread shutdownHelper = new Thread(() -> {
                do {
                    logger.warn("State machine queue is too busy. Try shutdown one second later.");
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        // ignore
                    }
                } while (!jobQueue.offer(shutdownJob));
            });
            shutdownHelper.setName("State-Machine-Caller-Shutdown-Helper");
            shutdownHelper.start();
        }

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
        return new StateMachineJob(() -> stateMachine.snapshot(snapshot.toByteArray()), future);
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
