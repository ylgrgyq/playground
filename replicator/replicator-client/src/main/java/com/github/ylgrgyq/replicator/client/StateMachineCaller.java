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

        if (!jobQueue.offer(StateMachineJob.newApplySnapshotJob(stateMachine, snapshot, future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_QUEUE_FULL));
        }

        return future;
    }

    public CompletableFuture<Void> applyLogs(List<byte[]> logs) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!jobQueue.offer(StateMachineJob.newApplyLogsJob(stateMachine, logs, future))) {
            future.completeExceptionally(new ReplicatorException(ReplicatorError.ESTATEMACHINE_QUEUE_FULL));
        }


        return future;
    }

    public void start() {
        Thread woker = new Thread(() -> {
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
        });

        woker.setName("StateMachine-Worker");
        woker.start();
    }

    public CompletableFuture<Void> shutdown() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        stop = true;

        Job shutdownJob = new ReplicatorClientInternalJob(() -> {
            // do nothing. Because it is only used as a mark which
            // indicate every job before is executed. So we can
            // shutdown safely.
        }, future);

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
            shutdownHelper.setName("StateMachine-Shutdown-Helper");
            shutdownHelper.start();
        }

        return future;
    }

    private static class Job implements Runnable {
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

        void done() {
            future.complete(null);
        }
    }

    private static class StateMachineJob extends Job {
        StateMachineJob(Runnable job, CompletableFuture<Void> future) {
            super(job, future);
        }

        static StateMachineJob newApplySnapshotJob(StateMachine stateMachine, Snapshot snapshot, CompletableFuture<Void> future) {
            return new StateMachineJob(() -> stateMachine.snapshot(snapshot.toByteArray()), future);
        }

        static StateMachineJob newApplyLogsJob(StateMachine stateMachine, List<byte[]> logs, CompletableFuture<Void> future) {
            return new StateMachineJob(() -> stateMachine.apply(logs), future);
        }
    }

    private static class ReplicatorClientInternalJob extends Job {
        ReplicatorClientInternalJob(Runnable job, CompletableFuture<Void> future) {
            super(job, future);
        }
    }
}
