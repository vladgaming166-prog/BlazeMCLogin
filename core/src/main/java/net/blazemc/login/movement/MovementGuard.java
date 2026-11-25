package net.blazemc.login.movement;

/**
 * Decides whether an unauthenticated movement packet should be rejected.
 * Look-only updates are always allowed. Walking is cancelled — never rewritten
 * with a teleport — so the client and server stay on the same last-valid position.
 */
public final class MovementGuard {

    private MovementGuard() {
    }

    public static boolean isLookOnly(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        return fromX == toX && fromY == toY && fromZ == toZ;
    }

    public static boolean hasHorizontalMove(double fromX, double fromZ, double toX, double toZ) {
        return fromX != toX || fromZ != toZ;
    }

    public static boolean exceedsVerticalDrift(double freezeY, double toY, double maxDrift) {
        return Math.abs(toY - freezeY) > Math.max(0.01, maxDrift);
    }

    /**
     * Cancel walking and large vertical changes. Gravity/idle jitter within
     * {@code maxVerticalDrift} is allowed so cancelling does not rubberband every tick.
     */
    public static boolean shouldCancelMove(
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ,
            double freezeY,
            double maxVerticalDrift
    ) {
        if (isLookOnly(fromX, fromY, fromZ, toX, toY, toZ)) {
            return false;
        }
        if (hasHorizontalMove(fromX, fromZ, toX, toZ)) {
            return true;
        }
        return exceedsVerticalDrift(freezeY, toY, maxVerticalDrift);
    }

    public static boolean needsEmergencyCorrection(
            double freezeX, double freezeY, double freezeZ,
            double x, double y, double z,
            double maxBlocks
    ) {
        double dx = x - freezeX;
        double dy = y - freezeY;
        double dz = z - freezeZ;
        double limit = Math.max(1.0, maxBlocks);
        return (dx * dx + dy * dy + dz * dz) > (limit * limit);
    }
}
