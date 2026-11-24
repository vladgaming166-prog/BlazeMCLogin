package net.blazemc.login.premium;

import java.util.UUID;

public record PremiumVerification(
        UUID uuid,
        String username,
        boolean premium,
        long timestampMillis,
        byte[] nonce,
        String detectorVersion
) {
    public PremiumVerification {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username");
        }
        if (nonce == null || nonce.length != 16) {
            throw new IllegalArgumentException("nonce must be 16 bytes");
        }
        if (detectorVersion == null) {
            detectorVersion = "unknown";
        }
    }
}
