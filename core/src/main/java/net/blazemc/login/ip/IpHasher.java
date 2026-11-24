package net.blazemc.login.ip;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.blazemc.login.security.HmacSha256;

public final class IpHasher {

    private final byte[] secret;

    public IpHasher(byte[] secret) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        if (this.secret.length < 16) {
            throw new IllegalArgumentException("IP HMAC secret must be at least 16 bytes");
        }
    }

    public String hashRaw(String rawAddress) {
        return hashNormalized(IpAddressNormalizer.normalize(rawAddress));
    }

    public String hashNormalized(String normalized) {
        return HmacSha256.signHex(secret, normalized.getBytes(StandardCharsets.UTF_8));
    }
}
