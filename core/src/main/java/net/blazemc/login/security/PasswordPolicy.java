package net.blazemc.login.security;

import net.blazemc.login.config.SecuritySettings;

public final class PasswordPolicy {

    public enum Failure {
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        TOO_COMMON,
        COMPLEXITY,
        MISMATCH
    }

    private final SecuritySettings settings;

    public PasswordPolicy(SecuritySettings settings) {
        this.settings = settings;
    }

    public Failure validate(String password, String confirm) {
        if (password == null || password.isEmpty()) {
            return Failure.EMPTY;
        }
        if (confirm != null && !password.equals(confirm)) {
            return Failure.MISMATCH;
        }
        int min = Math.max(1, settings.minimumPasswordLength());
        int max = Math.max(min, settings.maximumPasswordLength());
        if (password.length() < min) {
            return Failure.TOO_SHORT;
        }
        if (password.length() > max) {
            return Failure.TOO_LONG;
        }
        if (settings.rejectCommonPasswords() && CommonPasswords.isCommon(password)) {
            return Failure.TOO_COMMON;
        }
        if (settings.requireComplexity() && !meetsComplexity(password)) {
            return Failure.COMPLEXITY;
        }
        return null;
    }

    private boolean meetsComplexity(String password) {
        boolean letter = false;
        boolean digit = false;
        boolean upper = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
                if (Character.isUpperCase(c)) {
                    upper = true;
                }
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                special = true;
            }
        }
        boolean ok = letter && digit;
        if (settings.requireUppercase()) {
            ok = ok && upper;
        }
        if (settings.requireSpecialCharacter()) {
            ok = ok && special;
        }
        return ok;
    }
}
