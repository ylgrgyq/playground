package com.github.ylgrgyq.replicator.common;

import com.spotify.futures.CompletableFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public abstract class StartStopSupport<V, T, U, L> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(StartStopSupport.class);

    enum State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    /**
     * Used for invoking the extension points of this class
     */
    private final Executor executor;
    private final List<L> listeners;
    private CompletableFuture<?> future = CompletableFuture.completedFuture(null);
    private volatile State state;


    protected StartStopSupport(Executor executor) {
        this.executor = requireNonNull(executor, "executor");
        this.listeners = new CopyOnWriteArrayList<>();
        this.state = State.STOPPED;
    }

    public final void addListener(L listener) {
        listeners.add(requireNonNull(listener, "listener"));
    }

    public final boolean removeListener(L listener) {
        return listeners.remove(requireNonNull(listener, "listener"));
    }


    public final CompletableFuture<V> start(boolean failIfStarted) {
        return start(null, failIfStarted);
    }

    public final CompletableFuture<V> start(T arg, boolean failIfStarted) {
        return start(arg, null, failIfStarted);
    }

    public final synchronized CompletableFuture<V> start(T arg, U rollbackArg, boolean failIfStarted) {
        switch (state) {
            case STARTING:
            case STARTED:
                if (failIfStarted) {
                    return CompletableFutures.exceptionallyCompletedFuture(
                            new IllegalStateException("must be stopped to start; currently:" + state));
                } else {
                    @SuppressWarnings("unchecked")
                    final CompletableFuture<V> f = (CompletableFuture<V>) future;
                    return f;
                }
            case STOPPING:
                // start called during server is stopping, restart server when it stopped
                return future.exceptionally(ignored -> null)
                        .thenComposeAsync(ignored -> start(arg, rollbackArg, failIfStarted), executor);
        }

        assert state == State.STOPPED : "state:" + state;
        state = State.STARTING;

        CompletableFuture<V> startFuture = new CompletableFuture<>();
        boolean submitted = false;
        try {
            executor.execute(() -> {
                try {
                    notifyListeners(State.STARTING, arg, null, null);
                    final CompletionStage<V> s = doStart(arg);
                    if (s == null) {
                        throw new IllegalStateException("doStart() returned null");
                    }

                    s.whenComplete((result, cause) -> {
                        if (cause != null) {
                            startFuture.completeExceptionally(cause);
                        } else {
                            startFuture.complete(result);
                        }
                    });
                } catch (Exception e) {
                    startFuture.completeExceptionally(e);
                }
            });
            submitted = true;
        } catch (Exception e) {
            return CompletableFutures.exceptionallyCompletedFuture(e);
        } finally {
            // if start job has not been submitted, we can back to STOPPED state safely
            if (!submitted) {
                state = State.STOPPED;
            }
        }

        // we need to use handleAsync() to prevent current thread to handle startFuture if
        // startFuture is already done here
        final CompletableFuture<V> future = startFuture.handleAsync((result, cause) -> {
            if (cause != null) {
                final CompletableFuture<Void> rollbackFuture = stop(rollbackArg, true)
                        .exceptionally(rollbackCause -> {
                            rollbackFailed(rollbackCause);
                            return null;
                        });
                // wait for rollback but put the original Throwable into the future returned
                return rollbackFuture.<V>thenCompose(ignored -> CompletableFutures.exceptionallyCompletedFuture(cause));
            } else {
                enter(State.STARTED, arg, null, result);
                return CompletableFuture.completedFuture(result);
            }
        }, executor).thenCompose(Function.identity());

        this.future = future;
        return future;
    }

    public final CompletableFuture<Void> stop() {
        return stop(null);
    }

    public final CompletableFuture<Void> stop(U arg) {
        return stop(arg, false);
    }

    public final synchronized CompletableFuture<Void> stop(U arg, boolean rollback) {
        switch (state) {
            case STARTING:
                if (!rollback) {
                    // try stop when started and ignore any exception thrown during starting
                    return future.exceptionally(ignored -> null)
                            .thenComposeAsync(ignored -> stop(arg, false), executor);
                } else {
                    break;
                }
            case STOPPING:
            case STOPPED:
                @SuppressWarnings("unchecked")
                final CompletableFuture<Void> f = (CompletableFuture<Void>) future;
                return f;
        }

        assert state == State.STARTED || rollback : "state: " + state + ", rollback: " + rollback;
        state = State.STOPPING;

        CompletableFuture<Void> stopFuture = new CompletableFuture<>();
        final State prevState = state;
        boolean submitted = false;
        try {
            executor.execute(() -> {
                try {
                    notifyListeners(State.STOPPING, null, arg, null);
                    CompletionStage<Void> f = doStop(arg);
                    if (f == null) {
                        throw new IllegalStateException("doStop() returned null.");
                    }

                    f.whenComplete((result, cause) -> {
                        if (cause == null) {
                            stopFuture.complete(null);
                        } else {
                            stopFuture.completeExceptionally(cause);
                        }
                    });
                } catch (Exception ex) {
                    stopFuture.completeExceptionally(ex);
                }

            });
            submitted = true;
        } catch (Exception ex) {
            return CompletableFutures.exceptionallyCompletedFuture(ex);
        } finally {
            if (!submitted) {
                state = prevState;
            }
        }

        CompletableFuture<Void> future = stopFuture.whenCompleteAsync((result, cause) ->
                        enter(State.STOPPED, null, arg, null)
                , executor);

        this.future = future;
        return future;
    }

    public synchronized boolean isStarted() {
        return state == State.STARTED;
    }

    @Override
    public void close() {
        CompletableFuture<Void> f;
        synchronized (this) {
            if (state == State.STOPPED) {
                return;
            }

            f = stop(null);
        }

        boolean interrupted = false;
        for(;;) {
            try {
                f.get();
            } catch (InterruptedException ex) {
                interrupted = true;
            } catch (ExecutionException e) {
                closeFailed(e);
                break;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void enter(State state, T startArg, U stopArg, V startResult) {
        synchronized (this) {
            assert this.state != state : "transition to the same state:" + state;
            this.state = state;
        }

        notifyListeners(state, startArg, stopArg, startResult);
    }

    private void notifyListeners(State state, T startArg, U stopArg, V startResult) {
        for (L l : listeners) {
            try {
                switch (state) {
                    case STARTING:
                        notifyStarting(l, startArg);
                    case STARTED:
                        notifyStarted(l, startArg, startResult);
                    case STOPPING:
                        notifyStopping(l, stopArg);
                    case STOPPED:
                        notifyStopped(l, stopArg);
                    default:
                        throw new Error("unknown state:" + state);
                }
            } catch (Exception cause) {
                notificationFailed(l, cause);
            }
        }
    }

    protected abstract CompletionStage<V> doStart(T arg) throws Exception;

    protected abstract CompletionStage<Void> doStop(U arg) throws Exception;

    protected void notifyStarting(L listener, T arg) throws Exception {
    }

    protected void notifyStarted(L listener, T arg, V result) throws Exception {
    }

    protected void notifyStopping(L listener, U arg) throws Exception {
    }

    protected void notifyStopped(L listener, U arg) throws Exception {
    }

    protected void notificationFailed(L listener, Throwable throwable) {
        logger.warn("Failed to notify listener: {}", listener, throwable);
    }

    protected void rollbackFailed(Throwable throwable) {
        logStopFailure(throwable);
    }

    protected void closeFailed(Throwable throwable) {
        logStopFailure(throwable);
    }

    protected void logStopFailure(Throwable throwable) {
        logger.warn("Failed to stop: {}", throwable.getMessage(), throwable);
    }
}
