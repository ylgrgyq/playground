package com.github.ylgrgyq.resender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;

import static java.util.Objects.requireNonNull;

public final class AutomaticObjectQueueConsumer<E extends Payload> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AutomaticObjectQueueConsumer.class);
    private static final ThreadFactory threadFactory = new NamedThreadFactory("automatic-object-queue-consumer-");

    private final ObjectQueueConsumer<E> backupQueue;
    private final Thread worker;
    private final Executor listenerExecutor;
    private final Set<ConsumeObjectListener<E>> listeners;
    private volatile boolean stop;

    AutomaticObjectQueueConsumer(ObjectQueueConsumer<E> consumer, AutomaticObjectQueueConsumerBuilder builder) {
        requireNonNull(builder, "builder");

        this.backupQueue = consumer;
        this.worker = threadFactory.newThread(new Worker(builder.getHandler()));
        this.listenerExecutor = builder.getListenerExecutor();
        this.listeners = new CopyOnWriteArraySet<>();
    }

    public void start() {
        if (stop) {
            throw new IllegalStateException("consumer already stopped");
        }

        worker.start();
    }

    public void addListener(ConsumeObjectListener<E> listener) {
        requireNonNull(listener, "listener");

        listeners.add(listener);
    }

    public boolean removeListener(ConsumeObjectListener<E> listener) {
        requireNonNull(listener, "listener");

        return listeners.remove(listener);
    }

    @Override
    public void close() throws Exception {
        stop = true;

        if (worker != Thread.currentThread()) {
            worker.interrupt();
            worker.join();
        }
        backupQueue.close();
    }

    private final class Worker implements Runnable {
        private final ConsumeObjectHandler<E> handler;

        public Worker(ConsumeObjectHandler<E> handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            final ConsumeObjectHandler<E> handler = this.handler;
            final ObjectQueueConsumer<E> backupQueue = AutomaticObjectQueueConsumer.this.backupQueue;
            while (!stop) {
                boolean commit = false;
                try {
                    final E payload = backupQueue.fetch();

                    if (payload.isValid()) {
                        notifyInvalidPayload(payload);
                    } else {
                        try {
                            handler.onReceivedObject(payload);
                            notifyOnPayloadSendSuccess(payload);
                            commit = true;
                        } catch (Exception ex) {
                            notifyOnPayloadSendFailed(payload);
                            switch (handler.onReceivedObjectFailed(payload, ex)) {
                                case RETRY:
                                    break;
                                case IGNORE:
                                    commit = true;
                                    break;
                                case SHUTDOWN:
                                    close();
                                    break;
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    // do nothing
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

    private void notifyInvalidPayload(E failedPayload) {
        sendNotification(ConsumeObjectListener::onInvalidPayload, failedPayload);
    }

    private void notifyOnPayloadSendSuccess(E failedPayload) {
        sendNotification(ConsumeObjectListener::onPayloadSendSuccess, failedPayload);
    }

    private void notifyOnPayloadSendFailed(E failedPayload) {
        sendNotification(ConsumeObjectListener::onPayloadSendFailed, failedPayload);
    }

    private void sendNotification(BiConsumer<ConsumeObjectListener<E>, E> consumer, E payload) {
        try {
            listenerExecutor.execute(() -> {
                for (ConsumeObjectListener<E> l : listeners) {
                    try {
                        consumer.accept(l, payload);
                    } catch (Exception ex) {
                        l.onNotificationFailed(ex);
                    }
                }
            });
        } catch (Exception ex) {
            logger.error("Notification failed", ex);
        }
    }
}
