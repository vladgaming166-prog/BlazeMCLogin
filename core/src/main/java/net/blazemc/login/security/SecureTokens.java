package net.blazemc.login.security;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String randomHex(int bytes) {
        return HexFormat.of().formatHex(randomBytes(bytes));
    }

    public static String randomSecret() {
        return randomHex(32);
    }
}
