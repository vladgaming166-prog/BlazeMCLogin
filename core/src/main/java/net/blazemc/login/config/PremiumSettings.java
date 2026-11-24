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
        String sharedSecretHex
) {
    public static PremiumSettings defaults() {
        return new PremiumSettings(
                true, true, true, true,
                true, "blazemclogin:v1",
                15, 1500, ""
        );
    }
}
