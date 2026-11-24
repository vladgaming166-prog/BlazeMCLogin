package net.blazemc.login.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSha256 {

    private static final String ALG = "HmacSHA256";

    private HmacSha256() {
    }

    public static byte[] sign(byte[] secret, byte[] payload) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("HMAC secret must not be empty");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload");
        }
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(secret, ALG));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }

    public static byte[] sign(byte[] secret, String payload) {
        return sign(secret, payload.getBytes(StandardCharsets.UTF_8));
    }

    public static String signHex(byte[] secret, byte[] payload) {
        return HexFormat.of().formatHex(sign(secret, payload));
    }

    public static boolean verify(byte[] secret, byte[] payload, byte[] expectedMac) {
        if (expectedMac == null) {
            return false;
        }
        return ConstantTime.equals(sign(secret, payload), expectedMac);
    }
}
