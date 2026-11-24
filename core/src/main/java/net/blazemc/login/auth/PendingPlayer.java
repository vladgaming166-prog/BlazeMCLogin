package net.blazemc.login.auth;

import java.util.UUID;
import net.blazemc.login.premium.PremiumVerification;

public final class PendingPlayer {

    private final UUID uuid;
    private final String username;
    private final String ipHash;
    private final String normalizedIp;
    private final long joinedAt;
    private volatile boolean authenticated;
    private volatile boolean premiumVerified;
    private volatile AuthOutcome authSource;
    private volatile PremiumVerification premium;
    private volatile String frozenWorld;
    private volatile double frozenX;
    private volatile double frozenY;
    private volatile double frozenZ;
    private volatile float frozenYaw;
    private volatile float frozenPitch;

    public PendingPlayer(UUID uuid, String username, String ipHash, String normalizedIp, long joinedAt) {
        this.uuid = uuid;
        this.username = username;
        this.ipHash = ipHash;
        this.normalizedIp = normalizedIp;
        this.joinedAt = joinedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String username() {
        return username;
    }

    public String ipHash() {
        return ipHash;
    }

    public String normalizedIp() {
        return normalizedIp;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public boolean premiumVerified() {
        return premiumVerified;
    }

    public AuthOutcome authSource() {
        return authSource;
    }

    public PremiumVerification premium() {
        return premium;
    }

    public void markAuthenticated(AuthOutcome source) {
        this.authenticated = true;
        this.authSource = source;
    }

    public void markPremium(PremiumVerification verification) {
        this.premiumVerified = true;
        this.premium = verification;
    }

    public void clearAuth() {
        this.authenticated = false;
        this.authSource = AuthOutcome.LOGGED_OUT;
    }

    public void freezeAt(String world, double x, double y, double z, float yaw, float pitch) {
        this.frozenWorld = world;
        this.frozenX = x;
        this.frozenY = y;
        this.frozenZ = z;
        this.frozenYaw = yaw;
        this.frozenPitch = pitch;
    }

    public String frozenWorld() {
        return frozenWorld;
    }

    public double frozenX() {
        return frozenX;
    }

    public double frozenY() {
        return frozenY;
    }

    public double frozenZ() {
        return frozenZ;
    }

    public float frozenYaw() {
        return frozenYaw;
    }

    public float frozenPitch() {
        return frozenPitch;
    }

    public boolean timedOut(long now, int timeoutSeconds) {
        return !authenticated && now - joinedAt >= timeoutSeconds * 1000L;
    }
}
