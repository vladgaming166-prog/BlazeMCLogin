package net.blazemc.login.auth;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.blazemc.login.premium.PremiumVerification;

public final class PendingPlayer {

    public enum BridgeVerdict {
        NONE,
        PREMIUM,
        CRACKED
    }

    private final UUID uuid;
    private final String username;
    private final String ipHash;
    private final String normalizedIp;
    private final long joinedAt;
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean workInFlight = new AtomicBoolean(false);
    private volatile boolean premiumVerified;
    private volatile AuthOutcome authSource;
    private volatile PremiumVerification premium;
    private volatile BridgeVerdict bridgeVerdict = BridgeVerdict.NONE;
    private volatile String authProvider = "none";
    private volatile String frozenWorld;
    private volatile double frozenX;
    private volatile double frozenY;
    private volatile double frozenZ;
    private volatile float frozenYaw;
    private volatile float frozenPitch;
    private volatile boolean allowNextPluginTeleport;
    private volatile long lastEmergencyCorrectionMillis;

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
        return authenticated.get();
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

    public BridgeVerdict bridgeVerdict() {
        return bridgeVerdict;
    }

    public String authProvider() {
        return authProvider;
    }

    public void setAuthProvider(String provider) {
        this.authProvider = provider == null || provider.isBlank() ? "none" : provider;
    }

    public void setBridgeVerdict(BridgeVerdict verdict) {
        this.bridgeVerdict = verdict == null ? BridgeVerdict.NONE : verdict;
    }

    public void markAuthenticated(AuthOutcome source) {
        tryMarkAuthenticated(source);
    }

    public boolean tryMarkAuthenticated(AuthOutcome source) {
        if (!authenticated.compareAndSet(false, true)) {
            return false;
        }
        this.authSource = source;
        return true;
    }

    public boolean beginWork() {
        return workInFlight.compareAndSet(false, true);
    }

    public void endWork() {
        workInFlight.set(false);
    }

    public boolean workInFlight() {
        return workInFlight.get();
    }

    public void markPremium(PremiumVerification verification) {
        markPremium(verification, verification == null ? "unknown" : verification.detectorVersion());
    }

    public void markPremium(PremiumVerification verification, String provider) {
        this.premiumVerified = true;
        this.premium = verification;
        this.bridgeVerdict = BridgeVerdict.PREMIUM;
        setAuthProvider(provider);
    }

    public void markCrackedVerdict() {
        markCrackedVerdict("hmac-bridge");
    }

    public void markCrackedVerdict(String provider) {
        this.bridgeVerdict = BridgeVerdict.CRACKED;
        setAuthProvider(provider);
    }

    public void clearAuth() {
        this.authenticated.set(false);
        this.authSource = AuthOutcome.LOGGED_OUT;
        this.workInFlight.set(false);
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

    public void allowNextPluginTeleport() {
        this.allowNextPluginTeleport = true;
    }

    public boolean consumePluginTeleportPermit() {
        if (allowNextPluginTeleport) {
            allowNextPluginTeleport = false;
            return true;
        }
        return false;
    }

    public boolean canEmergencyCorrect(long now, long cooldownMillis) {
        if (now - lastEmergencyCorrectionMillis < cooldownMillis) {
            return false;
        }
        lastEmergencyCorrectionMillis = now;
        return true;
    }

    public boolean timedOut(long now, int timeoutSeconds) {
        return !authenticated() && now - joinedAt >= timeoutSeconds * 1000L;
    }
}
