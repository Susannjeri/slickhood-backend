package org.pms.silverocean.service.threadpooling;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PMSThreadPoolExecutor extends ThreadPoolExecutor implements PMSPoolRegistry.ManagedPool {

    private final String name;

    private PMSThreadPoolExecutor(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                 BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new NamedThreadFactory(name), handler);
        this.name = name;
        PMSPoolRegistry.register(this);
        log.info("ThreadPool '{}' created: MaxSize={}, QueueCapacity={}", name, maximumPoolSize, workQueue.remainingCapacity() + workQueue.size());
    }

    protected static PMSThreadPoolExecutor createInstance(String name, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                                   BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        return new PMSThreadPoolExecutor(name, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }


    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        log.info("[{}] Task starting on thread {}", name, t.getName());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t != null) {
            log.error("[{}] Task executed with exception in thread {}: {}", name, Thread.currentThread().getName(), t.getMessage());
        } else {
            log.info("[{}] Task finished successfully on thread {}", name, Thread.currentThread().getName());
        }
    }

    @Override
    public void execute(Runnable command) {
        try {
            super.execute(command);
            log.info("[{}] Task submitted. ActiveThreads={}, QueueSize={}", name, getActiveCount(), getQueue().size());
        } catch (RejectedExecutionException e) {
            // This will happen when the CallerRunsPolicy is full/rejected
            log.warn("[{}] Task rejected. Pool is full! MaxSize={}, QueueCapacity={}", name, getMaximumPoolSize(), getQueue().remainingCapacity());
            throw e; // Re-throw the exception after logging
        }
    }

    @Override
    public void shutdown() {
        PMSPoolRegistry.deregister(name);
        super.shutdown();
    }


    @Override
    public String poolName() {
        return name;
    }

    @Override
    public String stats() {
        return "type=platform, active=%d, poolSize=%d, completed=%d, queued=%d, max=%d"
                .formatted(getActiveCount(), getPoolSize(), getCompletedTaskCount(),
                        getQueue().size(), getMaximumPoolSize());
    }
}
