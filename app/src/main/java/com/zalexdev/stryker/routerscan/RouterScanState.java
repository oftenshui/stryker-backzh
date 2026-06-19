package com.zalexdev.stryker.routerscan;

import com.zalexdev.stryker.custom.Router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RouterScanState {

    private static final RouterScanState INSTANCE = new RouterScanState();

    public static RouterScanState get() {
        return INSTANCE;
    }

    public final List<Router> results = Collections.synchronizedList(new ArrayList<>());

    public volatile boolean running;
    public volatile int total;

    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger responsive = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();

    public List<String> ips = new ArrayList<>();
    public List<String> ports = new ArrayList<>();
    public int maxThreads = 50;
    public int timeout = 300;

    private RouterScanState() { }

    public void resetForBatch(List<String> ips, List<String> ports, int maxThreads, int timeout) {
        this.ips = new ArrayList<>(ips);
        this.ports = new ArrayList<>(ports);
        this.maxThreads = Math.max(1, maxThreads);
        this.timeout = timeout;
        this.total = this.ips.size() * this.ports.size();
        results.clear();
        completed.set(0);
        responsive.set(0);
        success.set(0);
    }

    public int incCompleted() { return completed.incrementAndGet(); }
    public int incResponsive() { return responsive.incrementAndGet(); }
    public int incSuccess() { return success.incrementAndGet(); }

    public int getCompleted() { return completed.get(); }
    public int getResponsive() { return responsive.get(); }
    public int getSuccess() { return success.get(); }

    public int percent() {
        int t = total;
        if (t <= 0) return 0;
        return Math.min(100, getCompleted() * 100 / t);
    }

    public ArrayList<Router> getGood() {
        ArrayList<Router> good = new ArrayList<>();
        synchronized (results) {
            for (Router r : results) {
                if (r.getType() == 1) good.add(r);
            }
        }
        return good;
    }
}
