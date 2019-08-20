package com.github.ylgrgyq.reservoir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public final class AutomaticObjectQueueConsumer<E extends Verifiable> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AutomaticObjectQueueConsumer.class);
    private static final ThreadFactory threadFactory = new NamedThreadFactory("automatic-object-queue-consumer-");

    private final ObjectQueueConsumer<E> backupQueue;
    private final Thread worker;
    private final Executor listenerExecutor;
    private final Set<ConsumeObjectListener<E>> listeners;
    private volatile boolean closed;

    AutomaticObjectQueueConsumer(AutomaticObjectQueueConsumerBuilder<E> builder) {
        requireNonNull(builder, "builder");

        this.backupQueue = builder.getConsumer();
        this.worker = threadFactory.newThread(new Worker(builder.getConsumeObjectHandler()));
        this.listenerExecutor = builder.getListenerExecutor();
        this.listeners = new CopyOnWriteArraySet<>(builder.getConsumeElementListeners());
        this.worker.start();
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
        closed = true;

        if (worker != Thread.currentThread()) {
            worker.interrupt();
            worker.join();
        }
        backupQueue.close();
    }

    boolean closed() {
        return closed;
    }

    private final class Worker implements Runnable {
        private final ConsumeObjectHandler<E> handler;

        public Worker(ConsumeObjectHandler<E> handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            final ConsumeObjectHandler<E> handler = this.handler;
            final ObjectQueueConsumer<E> consumer = AutomaticObjectQueueConsumer.this.backupQueue;
            while (!closed) {
                boolean commit = false;
                try {
                    final E obj = consumer.fetch();

                    if (!obj.isValid()) {
                        notifyInvalidObject(obj);
                        commit = true;
                    } else {
                        try {
                            handler.onHandleObject(obj);
                            notifyOnHandleSuccess(obj);
                            commit = true;
                        } catch (Exception ex) {
                            notifyOnHandleFailed(obj, ex);
                            commit = handleObjectFailed(handler, obj, ex);
                        }
                    }
                } catch (InterruptedException ex) {
                    // do nothing
                } catch (Exception ex) {
                    logger.warn("Got unexpected exception on processing payload in backup queue reservoir.", ex);
                } finally {
                    if (commit) {
                        consumer.commit();
                    }
                }
            }
        }
    }

    private boolean handleObjectFailed(ConsumeObjectHandler<E> handler, E obj, Throwable ex) throws Exception {
        boolean commit = false;
        HandleFailedStrategy strategy;
        try {
            strategy = handler.onHandleObjectFailed(obj, ex);
            if (strategy == null) {
                logger.error("ConsumeObjectHandler.onHandleObjectFailed returned null on handle" +
                                " object {}, and exception {}, shutdown anyway.",
                        obj, ex);
                strategy = HandleFailedStrategy.SHUTDOWN;
            }
        } catch (Exception ex2) {
            logger.error("Got unexpected exception from ConsumeObjectHandler.onHandleObjectFailed on handle " +
                    "object {}, and exception {}. Shutdown anyway.", obj, ex2);
            strategy = HandleFailedStrategy.SHUTDOWN;
        }

        switch (strategy) {
            case RETRY:
                break;
            case IGNORE:
                commit = true;
                break;
            case SHUTDOWN:
                close();
                break;
        }
        return commit;
    }

    private void notifyInvalidObject(E obj) {
        sendNotification(l -> l.onInvalidObject(obj));
    }

    private void notifyOnHandleSuccess(E obj) {
        sendNotification(l -> l.onHandleSuccess(obj));
    }

    private void notifyOnHandleFailed(E obj, Throwable ex) {
        sendNotification(l -> l.onHandleFailed(obj, ex));
    }

    private void sendNotification(Consumer<ConsumeObjectListener<E>> consumer) {
        try {
            listenerExecutor.execute(() -> {
                for (ConsumeObjectListener<E> l : listeners) {
                    try {
                        consumer.accept(l);
                    } catch (Exception ex) {
                        l.onListenerNotificationFailed(ex);
                    }
                }
            });
        } catch (Exception ex) {
            logger.error("Notification failed", ex);
        }
    }
}
