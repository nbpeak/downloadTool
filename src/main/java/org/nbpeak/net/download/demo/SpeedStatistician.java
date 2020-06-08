package org.nbpeak.net.download.demo;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SpeedStatistician {
    private SpeedNotifyEvent speedNotifyEvent;
    private AtomicLong counter = new AtomicLong(0);
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
    private long preCount = 0;

    public SpeedStatistician(SpeedNotifyEvent speedNotifyEvent) {
        this.speedNotifyEvent = speedNotifyEvent;
    }

    public void add(long val) {
        counter.addAndGet(val);
    }

    public void start() {
        if (!scheduledThreadPoolExecutor.isShutdown()) {
            scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
                long nowCount = counter.get();
                speedNotifyEvent.event(nowCount - preCount);
                preCount = nowCount;
            }, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        scheduledThreadPoolExecutor.shutdown();
    }
}
