package net.blazemc.login.premium;

import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NonceCache {

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public NonceCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public boolean seen(byte[] nonce, long now) {
        purge(now);
        String key = HexFormat.of().formatHex(nonce);
        Long previous = seen.putIfAbsent(key, now);
        return previous != null;
    }

    public void purge(long now) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > ttlMillis) {
                iterator.remove();
            }
        }
    }

    public int size() {
        return seen.size();
    }
}
