package net.blazemc.login.config;

public record MovementSettings(
        boolean enabled,
        boolean preventPositionDesync,
        boolean preventUnexpectedTeleports,
        boolean freezeUnauthenticated,
        double maxVerticalDrift,
        double emergencyCorrectionBlocks
) {
    public static MovementSettings defaults() {
        return new MovementSettings(true, true, true, true, 0.8, 2.5);
    }
}
