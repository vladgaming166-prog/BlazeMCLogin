package net.blazemc.login.config;

public record SecuritySettings(
        int maxLoginAttempts,
        int lockDurationSeconds,
        int loginTimeoutSeconds,
        int minimumPasswordLength,
        int maximumPasswordLength,
        boolean rejectCommonPasswords,
        boolean requireComplexity,
        boolean requireUppercase,
        boolean requireSpecialCharacter,
        boolean rateLimitEnabled,
        int attemptsPerMinute,
        String hashingAlgorithm,
        int argonMemoryKib,
        int argonIterations,
        int argonParallelism,
        String ipHmacSecretHex,
        String premiumHmacSecretHex
) {
    public static SecuritySettings defaults() {
        return new SecuritySettings(
                5, 300, 60, 8, 64,
                true, false, false, false,
                true, 10,
                "ARGON2ID", 65536, 3, 1,
                "", ""
        );
    }
}
