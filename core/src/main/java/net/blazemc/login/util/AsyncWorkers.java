package net.blazemc.login.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncWorkers implements AutoCloseable {

    private final ExecutorService hashing;
    private final ExecutorService database;

    public AsyncWorkers(int threads) {
        int n = Math.max(2, threads);
        this.hashing = Executors.newFixedThreadPool(Math.min(4, n), named("blazemclogin-hash"));
        this.database = Executors.newFixedThreadPool(n, named("blazemclogin-db"));
    }

    public ExecutorService hashing() {
        return hashing;
    }

    public ExecutorService database() {
        return database;
    }

    @Override
    public void close() {
        shutdown(hashing);
        shutdown(database);
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + n.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
