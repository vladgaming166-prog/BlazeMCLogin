package net.blazemc.login.security;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class ConstantTime {

    private ConstantTime() {
    }

    public static boolean equals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }

    public static boolean equals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
