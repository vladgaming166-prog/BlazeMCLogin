package net.blazemc.login.premium;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds HMAC-verified bridge packets that arrive before PlayerJoinEvent.
 */
public final class PremiumPacketBuffer {

    private final ConcurrentHashMap<UUID, PremiumVerification> pending = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public PremiumPacketBuffer(long ttlMillis) {
        this.ttlMillis = Math.max(1_000L, ttlMillis);
    }

    public void store(PremiumVerification verification) {
        if (verification == null) {
            return;
        }
        pending.put(verification.uuid(), verification);
    }

    public PremiumVerification take(UUID uuid, long nowMillis) {
        PremiumVerification verification = pending.remove(uuid);
        if (verification == null) {
            return null;
        }
        if (Math.abs(nowMillis - verification.timestampMillis()) > ttlMillis) {
            return null;
        }
        return verification;
    }

    public void discard(UUID uuid) {
        pending.remove(uuid);
    }

    public int size() {
        return pending.size();
    }
}
