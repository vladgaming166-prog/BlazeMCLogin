package net.blazemc.login.model;

public enum AccountType {
    PREMIUM,
    CRACKED,
    UNKNOWN;

    public static AccountType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return AccountType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
