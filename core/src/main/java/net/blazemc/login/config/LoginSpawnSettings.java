package net.blazemc.login.config;

public record LoginSpawnSettings(
        boolean enabled,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public static LoginSpawnSettings defaults() {
        return new LoginSpawnSettings(false, "world", 0.5, 80.0, 0.5, 0f, 0f);
    }
}
