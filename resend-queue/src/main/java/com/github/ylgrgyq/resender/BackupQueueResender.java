package com.github.ylgrgyq.resender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class BackupQueueResender<E> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BackupQueueResender.class);

    private final ResendQueueConsumer<PayloadCarrier<E>> backupQueue;
    private final BackupQueueHandler<PayloadCarrier<E>> handler;
    private final ResenderListener<E> listener;
    private final Executor listenerExecutor;
    private final Thread worker;
    private volatile boolean stop;

    public BackupQueueResender(ResendQueueConsumer<PayloadCarrier<E>> queue,
                               BackupQueueHandler<PayloadCarrier<E>> handler,
                               ResenderListener<E> listener,
                               Executor listenerExecutor) {
        this.backupQueue = queue;
        this.handler = handler;
        this.worker = new Thread(new Worker());
        this.listener = listener;
        this.listenerExecutor = listenerExecutor;
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
                boolean commit = false;
                try {
                    PayloadCarrier<E> payload = backupQueue.fetch();
                    if (payload.isValid()) {
                        onInvalidPayload(payload);
                    } else {
                        try {
                            handler.handleBackupPayload(payload);
                            onPayloadSendSuccess(payload);
                            commit = true;
                        } catch (Exception ex) {
                            onPayloadSendFailed(payload);
                            commit = handler.handleFailedPayload(payload, ex);
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Got unexpected exception on processing payload in backup queue resender.", ex);
                } finally {
                    if (commit) {
                        backupQueue.commit();
                    }
                }
            }
        }
    }

    private void onInvalidPayload(PayloadCarrier<E> payload) {
        notification(() -> listener.onInvalidPayload(payload));
    }

    private void onPayloadSendSuccess(PayloadCarrier<E> payload) {
        notification(() -> listener.onPayloadSendSuccess(payload));
    }

    private void onPayloadSendFailed(PayloadCarrier<E> payload) {
        notification(() -> listener.onPayloadSendFailed(payload));
    }

    private void notification(Runnable runnable) {
        try {
            listenerExecutor.execute(() -> {
                try {
                    runnable.run();
                } catch (Exception ex) {
                    listener.onNotificationFailed(ex);
                }
            });
        } catch (Exception ex) {
            logger.error("Notification failed", ex);
        }

    }
}
