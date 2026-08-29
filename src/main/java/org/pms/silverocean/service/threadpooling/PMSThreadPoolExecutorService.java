package org.pms.silverocean.service.threadpooling;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class PMSThreadPoolExecutorService {
    private final String poolName;
    private static final String SCHEDULER_NAME = "PMS-Scheduler";
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerPool;

    private PMSThreadPoolExecutorService (String poolName, ExecutorService workerPool) {
        this.poolName = poolName;
        this.scheduler = Executors.newScheduledThreadPool(1, new NamedThreadFactory(SCHEDULER_NAME));
        this.workerPool = workerPool;
    }

    protected static PMSThreadPoolExecutorService createInstance(String poolName, ExecutorService workerPool) {
        return new PMSThreadPoolExecutorService(poolName, workerPool);
    }

    /**
     * Schedule a fire-and-forget task after a delay
     *
     * @return
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        log.info("Thread {} scheduling task '{}' in {} ms", poolName, task, unit.toMillis(delay));
        return scheduler.schedule(() -> workerPool.submit(task), delay, unit);
    }

    /**
     * Schedule a fire-and-forget task at fixed rate
     */
    public void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        log.info("Thread {} scheduling fixed rate task '{}' in {} ms to run every {} {}", poolName, task, unit.toMillis(initialDelay), period, unit);
        scheduler.scheduleAtFixedRate(() -> workerPool.submit(task), initialDelay, period, unit);
    }

    /**
     * Schedule a task with a result (CompletableFuture) after a delay
     */
    public <T> CompletableFuture<T> schedule(Supplier<T> task, long delay, TimeUnit unit) {
        log.info("Thread {} scheduling returnable task '{}' in {} ms", poolName, task, unit.toMillis(delay));
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(() -> {
            CompletableFuture.supplyAsync(task, workerPool)
                    .thenAccept(future::complete)
                    .exceptionally(ex -> {
                        Throwable cause = ex instanceof CompletionException && ex.getCause() != null
                                ? ex.getCause() : ex;
                        future.completeExceptionally(cause);
                        return null;
                    });
        }, delay, unit);
        return future;
    }

    /**
     * Execute an immediate async task with a result
     */
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, workerPool)
                .exceptionallyCompose(ex ->
                        CompletableFuture.failedFuture(unwrap(ex)));
    }

    /**
     * Execute an immediate async task without a result
     */
    public CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(task, workerPool);
    }

    /**
     * Graceful shutdown
     */
    public void shutdown() {
        log.info("Thread {} shutting down", poolName);
        scheduler.shutdown();
        workerPool.shutdown();
    }

    private static Throwable unwrap(Throwable ex) {
        while ((ex instanceof CompletionException || ex instanceof ExecutionException)
                && ex.getCause() != null) {
            ex = ex.getCause();
        }
        return ex;
    }
}
