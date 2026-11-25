package net.blazemc.login.config;

public record CrackedSettings(
        boolean enabled,
        boolean requireLogin,
        boolean requireRegistration
) {
    public static CrackedSettings defaults() {
        return new CrackedSettings(true, true, true);
    }
}
