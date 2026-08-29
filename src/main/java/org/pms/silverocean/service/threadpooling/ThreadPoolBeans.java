package org.pms.silverocean.service.threadpooling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ThreadPoolBeans {
    private final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private final AtomicInteger allocatedCPUProcessors = new AtomicInteger(0);

    public PMSThreadPoolExecutorService ioExecutorService(String poolName) {
        ExecutorService virtualExecutor = PMSVirtualExecutor.createInstance(poolName, null);
        return PMSThreadPoolExecutorService.createInstance(poolName, virtualExecutor);
    }

    public PMSThreadPoolExecutorService ioExecutorService(String poolName, Integer maxConcurrency) {
        ExecutorService virtualExecutor = PMSVirtualExecutor.createInstance(poolName, maxConcurrency);
        return PMSThreadPoolExecutorService.createInstance(poolName, virtualExecutor);
    }


    public PMSThreadPoolExecutorService cpuExecutorService(String poolName, int maxPoolSize, int queueCapacity) {
        int currentAllocated = allocatedCPUProcessors.get();
        int remaining = CPU_COUNT - currentAllocated;
        int actualPoolSize = Math.max(1, Math.min(maxPoolSize, remaining));
        allocatedCPUProcessors.addAndGet(actualPoolSize);
        if (remaining <= 0) {
            log.warn("CPU resources exhausted for pool {}. Resource starvation imminent!", poolName);
        }
        log.info("Creating CPU Pool [{}] with size: {} (Requested: {}, System Total: {})",
                poolName, actualPoolSize, maxPoolSize, CPU_COUNT);

        ExecutorService cpuWorkerPool = PMSThreadPoolExecutor.createInstance(
                poolName,
                1,
                actualPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return PMSThreadPoolExecutorService.createInstance(poolName, cpuWorkerPool);
    }
}
