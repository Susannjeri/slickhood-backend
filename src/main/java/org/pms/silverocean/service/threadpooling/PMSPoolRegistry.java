package org.pms.silverocean.service.threadpooling;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PMSPoolRegistry {
    private static final Map<String, ManagedPool> POOLS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService MONITOR =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("threadpool-monitor"));

    static {
        MONITOR.scheduleAtFixedRate(() -> {
            POOLS.values().forEach(pool -> log.info(
                    "[{}] Stats -> [{}]",
                    pool.poolName(), pool.stats()
            ));
        }, 5, 60, TimeUnit.MINUTES);
    }

    public interface ManagedPool {
        String poolName();
        String stats();
    }

    private PMSPoolRegistry() {}

    static void register(ManagedPool pool)  { POOLS.put(pool.poolName(), pool); }
    static void deregister(String name)     { POOLS.remove(name); }
    public static Map<String, ManagedPool> pools() { return Map.copyOf(POOLS); }
}
