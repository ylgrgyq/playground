package com.github.ylgrgyq.reservoir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
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
    @Nullable
    private final Executor listenerExecutor;
    private final Set<ConsumeObjectListener<E>> listeners;
    private volatile boolean closed;

    public AutomaticObjectQueueConsumer(ObjectQueue<E> queue,
                                        ConsumeObjectHandler<E> handler) {
        this(queue, handler, null, Collections.emptyList());
    }

    public AutomaticObjectQueueConsumer(ObjectQueue<E> queue,
                                        ConsumeObjectHandler<E> handler,
                                        @Nullable Executor listenerExecutor) {
        this(queue, handler, listenerExecutor, Collections.emptyList());
    }

    public AutomaticObjectQueueConsumer(ObjectQueue<E> queue,
                                        ConsumeObjectHandler<E> handler,
                                        @Nullable Executor listenerExecutor,
                                        List<ConsumeObjectListener<E>> listeners) {
        requireNonNull(queue, "queue");
        requireNonNull(handler, "handler");
        requireNonNull(listeners, "listeners");

        if (listenerExecutor == null && !listeners.isEmpty()) {
            throw new IllegalArgumentException("listenerExecutor is null but listeners is not empty");
        }

        this.backupQueue = queue;
        this.worker = threadFactory.newThread(new Worker(handler));
        this.listenerExecutor = listenerExecutor;
        this.listeners = new CopyOnWriteArraySet<>(listeners);
        this.worker.start();
    }

    public void addListener(ConsumeObjectListener<E> listener) {
        requireNonNull(listener, "listener");

        if (listenerExecutor == null) {
            throw new IllegalStateException("listenerExecutor is null");
        }

        listeners.add(listener);
    }

    public boolean removeListener(ConsumeObjectListener<E> listener) {
        requireNonNull(listener, "listener");

        if (listenerExecutor == null) {
            throw new IllegalStateException("listenerExecutor is null");
        }

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

        Worker(ConsumeObjectHandler<E> handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            final ConsumeObjectHandler<E> handler = this.handler;
            final ObjectQueueConsumer<E> consumer = AutomaticObjectQueueConsumer.this.backupQueue;
            while (!closed) {
                try {
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
                    } finally {
                        if (commit) {
                            consumer.commit();
                        }
                    }
                } catch (InterruptedException ex) {
                    // do nothing
                } catch (Exception ex) {
                    logger.warn("Got unexpected exception on processing object in reservoir queue.", ex);
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
        if (listenerExecutor == null) {
            return;
        }

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
