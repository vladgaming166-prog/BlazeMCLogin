package net.blazemc.login.security;

import java.util.concurrent.ConcurrentHashMap;

public final class LoginAttemptTracker {

    public record State(int failures, long lockedUntilMillis) {
        public boolean locked(long now) {
            return lockedUntilMillis > now;
        }
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long lockMillis;

    public LoginAttemptTracker(int maxAttempts, int lockDurationSeconds) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockMillis = Math.max(1, lockDurationSeconds) * 1000L;
    }

    public boolean isLocked(String key, long now) {
        State state = states.get(key);
        return state != null && state.locked(now);
    }

    public long remainingLockMillis(String key, long now) {
        State state = states.get(key);
        if (state == null || !state.locked(now)) {
            return 0L;
        }
        return state.lockedUntilMillis - now;
    }

    public State recordFailure(String key, long now) {
        return states.compute(key, (ignored, current) -> {
            if (current == null || (current.lockedUntilMillis != 0 && current.lockedUntilMillis <= now)) {
                current = new State(0, 0);
            }
            int failures = current.failures + 1;
            long lockUntil = failures >= maxAttempts ? now + lockMillis : 0L;
            return new State(failures, lockUntil);
        });
    }

    public void clear(String key) {
        states.remove(key);
    }
}
