package org.pms.silverocean.service.threadpooling;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory  implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public NamedThreadFactory(String poolName) {
        this.namePrefix = poolName + "-thread-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
        t.setDaemon(false); // Often good practice for worker threads
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}
