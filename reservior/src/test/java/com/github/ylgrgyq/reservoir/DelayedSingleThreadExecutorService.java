package com.github.ylgrgyq.reservoir;

import java.util.List;
import java.util.concurrent.*;

public class DelayedSingleThreadExecutorService extends AbstractExecutorService {
    private static final ThreadFactory threadFactory = new NamedThreadFactory("delayed-single-thread-pool-");
    private final long defaultDelayedMillis;
    private final ExecutorService executorService;

    public DelayedSingleThreadExecutorService() {
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.defaultDelayedMillis = 0;
    }

    public DelayedSingleThreadExecutorService(long defaultDelayed, TimeUnit unit) {
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
        this.defaultDelayedMillis = unit.toMillis(defaultDelayed);
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        executorService.execute(() -> {
            if (!(command instanceof DelayedRunnable)) {
                boolean interrupted = false;
                try {
                    Thread.sleep(defaultDelayedMillis);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }

                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            command.run();
        });
    }

    public static class DelayedRunnable implements Runnable {
        private final CountDownLatch latch;
        private final long delayMillis;
        private final Runnable command;

        public DelayedRunnable(Runnable command, long delayMillis) {
            this.delayMillis = delayMillis;
            this.latch = new CountDownLatch(1);
            this.command = command;
        }

        public void breakDelay() {
            latch.countDown();
        }

        @Override
        public void run() {
            boolean interrupted = false;
            try {
                latch.await(delayMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                interrupted = true;
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            command.run();
        }
    }
}
