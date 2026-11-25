package net.blazemc.login.config;

public record PremiumSettings(
        boolean autoLoginEnabled,
        boolean successMessage,
        boolean requireVerifiedIdentity,
        boolean bypassPassword,
        boolean bridgeEnabled,
        String channel,
        int maxMessageAgeSeconds,
        int waitForBridgeMillis,
        String sharedSecretHex,
        boolean disablePasswordLogin,
        boolean disableRegistration,
        boolean protectReservedNames,
        boolean rejectUnverifiedSessions
) {
    public static PremiumSettings defaults() {
        return new PremiumSettings(
                true, true, true, true,
                true, "blazemclogin:v1",
                15, 2500, "",
                true, true, true, true
        );
    }

    public boolean autoAuthEnabled() {
        return autoLoginEnabled;
    }
}
