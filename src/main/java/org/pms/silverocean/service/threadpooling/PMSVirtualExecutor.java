package org.pms.silverocean.service.threadpooling;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
public class PMSVirtualExecutor extends AbstractExecutorService implements PMSPoolRegistry.ManagedPool {
    private final String name;
    private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
    private final LongAdder submitted = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Semaphore permits;          // null = unbounded

    private PMSVirtualExecutor(String name, Integer maxConcurrency) {
        this.name = name;
        if (maxConcurrency != null && maxConcurrency == 0) {
            throw new RuntimeException("Invalid maxConcurrency config. 0 is not accepted as a valid value for " + name + " threadpool");
        }
        this.permits = maxConcurrency == null ? null : new Semaphore(maxConcurrency);
        PMSPoolRegistry.register(this);
        log.info("Virtual pool '{}' created, maxConcurrency={}", name,
                maxConcurrency == null ? "unbounded" : maxConcurrency);
    }

    static PMSVirtualExecutor createInstance(String name, Integer maxConcurrency) {
        return new PMSVirtualExecutor(name, maxConcurrency);
    }

    @Override
    public void execute(Runnable command) {
        submitted.increment();
        delegate.execute(() -> {
            if (permits != null) permits.acquireUninterruptibly();
            inFlight.incrementAndGet();
            try {
                command.run();
                completed.increment();
            } catch (Throwable t) {
                failed.increment();
                log.error("[{}] Task failed on {}: {}", name, Thread.currentThread(), t.getMessage());
                throw t;
            } finally {
                inFlight.decrementAndGet();
                if (permits != null) permits.release();
            }
        });
    }

    @Override
    public String poolName() { return name; }

    @Override
    public String stats() {
        return "type=virtual, submitted=%d, inFlight=%d, completed=%d, failed=%d, waitingForPermit=%d"
                .formatted(submitted.sum(), inFlight.get(), completed.sum(), failed.sum(),
                        permits == null ? 0 : permits.getQueueLength());
    }

    @Override
    public void shutdown() { PMSPoolRegistry.deregister(name); delegate.shutdown(); }
    @Override
    public List<Runnable> shutdownNow() { PMSPoolRegistry.deregister(name); return delegate.shutdownNow(); }
    @Override
    public boolean isShutdown() { return delegate.isShutdown(); }
    @Override
    public boolean isTerminated() { return delegate.isTerminated(); }
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}