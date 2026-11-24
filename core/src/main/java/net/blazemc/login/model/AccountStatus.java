package net.blazemc.login.model;

public enum AccountStatus {
    ACTIVE,
    LOCKED,
    RESET_REQUIRED;

    public static AccountStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
