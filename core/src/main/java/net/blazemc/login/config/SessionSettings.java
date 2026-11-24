package net.blazemc.login.config;

public record SessionSettings(
        boolean enabled,
        boolean rememberAfterReconnect,
        int expirationMinutes,
        boolean bindToIp,
        boolean invalidateOnIpChange,
        boolean invalidateOnPasswordChange,
        boolean preventMultipleSessions
) {
    public static SessionSettings defaults() {
        return new SessionSettings(true, true, 30, false, true, true, false);
    }
}
