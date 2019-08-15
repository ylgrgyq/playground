package com.github.ylgrgyq.resender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class BackupQueueResender implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BackupQueueResender.class);

    private final BackupQueue<PayloadCarrier> backupQueue;
    private final Consumer<? super PayloadCarrier> resendConsumer;
    private final Thread worker;
    private volatile boolean stop;

    public BackupQueueResender(BackupQueue<PayloadCarrier> queue, Consumer<? super PayloadCarrier> resendConsumer) {
        this.backupQueue = queue;
        this.resendConsumer = resendConsumer;
        this.worker = new Thread(new Worker());
    }

    public void start() {
        this.worker.start();
    }

    @Override
    public void close() throws Exception {
        stop = true;

        worker.join();
    }

    private final class Worker implements Runnable {
        @Override
        public void run() {
            while (!stop) {
                PayloadCarrier payload = backupQueue.peekFirst();
                try {
                    if (payload != null) {
                        long delayed = payload.getDelay(TimeUnit.NANOSECONDS);
                        if (delayed > 0) {
                            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(delayed));
                        } else {
                            try {
                                if (payload.noNeedToResend()) {
                                    logger.warn("Backup payload {} dropped due to no need to resend.", payload);
                                } else {
                                    try {
                                        resendConsumer.accept(payload);
                                    } catch (Exception ex) {
                                        payload.increaseFailedTimes();
                                        backupQueue.putLast(payload);
                                    }
                                }
                                backupQueue.pollFirst();
                            } finally {
                                backupQueue.persistent();
                            }
                        }
                    } else {
                        PayloadCarrier nextPayload = null;
                        for (; !stop && nextPayload == null; ) {
                            // wait at most 5 seconds to check if worker needs to stop
                            nextPayload = backupQueue.pollFirst(5, TimeUnit.SECONDS);
                        }

                        if (nextPayload != null) {
                            backupQueue.putFirst(nextPayload);
                        }
                    }
                } catch (InterruptedException ex) {
                    // do nothing
                } catch (Exception ex) {
                    logger.warn("Got unexpected exception on processing payload {} in backup queue resender.", payload, ex);
                }
            }
        }
    }
}
