package net.blazemc.login.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final int maxPerMinute;
    private final long windowMillis;

    public RateLimiter(int maxPerMinute) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
        this.windowMillis = 60_000L;
    }

    public boolean allow(String key, long now) {
        Deque<Long> times = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            if (times.size() >= maxPerMinute) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    public void purge(long now) {
        Iterator<Map.Entry<String, Deque<Long>>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            Deque<Long> times = entry.getValue();
            synchronized (times) {
                prune(times, now);
                if (times.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    private void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > windowMillis) {
            times.removeFirst();
        }
    }
}
