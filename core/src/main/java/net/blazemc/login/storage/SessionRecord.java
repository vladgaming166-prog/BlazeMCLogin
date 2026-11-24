package net.blazemc.login.storage;

import java.util.UUID;

public record SessionRecord(
        String sessionIdHash,
        UUID uuid,
        long createdAt,
        long expiresAt,
        String ipHash,
        int passwordVersion
) {
    public boolean expired(long now) {
        return expiresAt <= now;
    }
}
