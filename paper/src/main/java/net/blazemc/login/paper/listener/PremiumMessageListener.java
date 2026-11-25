package net.blazemc.login.paper.listener;

import java.util.Map;
import java.util.logging.Level;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.auth.AuthOutcome;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.paper.PaperPlayers;
import net.blazemc.login.paper.premium.TrustedAuthProviders;
import net.blazemc.login.premium.PremiumVerifier;
import net.blazemc.login.premium.VerificationOutcome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public final class PremiumMessageListener implements PluginMessageListener {

    private final BlazeMCLoginPlugin plugin;

    public PremiumMessageListener(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!PluginConstants.CHANNEL_ID.equals(channel) && !"blazemclogin:v1".equalsIgnoreCase(channel)) {
            return;
        }
        if (!plugin.settings().premium().bridgeEnabled()) {
            plugin.logAuth(player, TrustedAuthProviders.HMAC_BRIDGE, "UNKNOWN", "DISABLED",
                    "IGNORE", "Premium HMAC bridge is disabled");
            return;
        }
        plugin.workers().database().execute(() -> handle(player, message));
    }

    private void handle(Player player, byte[] message) {
        try {
            PremiumVerifier.VerificationResult result = plugin.premiumVerifier().inspect(
                    message,
                    plugin.premiumSecret(),
                    plugin.settings().premium(),
                    player.getUniqueId(),
                    player.getName(),
                    System.currentTimeMillis()
            );
            if (result.outcome() == VerificationOutcome.MISSING_SECRET
                    || result.outcome() == VerificationOutcome.BAD_SIGNATURE
                    || result.outcome() == VerificationOutcome.MALFORMED
                    || result.outcome() == VerificationOutcome.EXPIRED
                    || result.outcome() == VerificationOutcome.REPLAY
                    || result.outcome() == VerificationOutcome.UUID_MISMATCH
                    || result.outcome() == VerificationOutcome.USERNAME_MISMATCH) {
                plugin.logAuth(player, TrustedAuthProviders.HMAC_BRIDGE, "UNVERIFIED",
                        result.outcome().name(), "IGNORE", "HMAC packet rejected; not used as Premium or cracked proof");
                return;
            }
            PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
            if (result.outcome() == VerificationOutcome.CRACKED_SESSION) {
                plugin.logAuth(player, TrustedAuthProviders.HMAC_BRIDGE, "CRACKED",
                        "CRACKED_SESSION", "CRACKED_FLOW", "Signed cracked verdict from Velocity");
                if (pending != null) {
                    pending.markCrackedVerdict(TrustedAuthProviders.HMAC_BRIDGE);
                    if (!pending.authenticated()) {
                        AuthResult auth = plugin.auth().handleJoin(pending, PaperPlayers.hasBypassLogin(player), System.currentTimeMillis());
                        plugin.getServer().getScheduler().runTask(plugin, () -> notifyResult(player, auth, TrustedAuthProviders.HMAC_BRIDGE));
                    }
                } else if (result.verification() != null) {
                    plugin.premiumPackets().store(result.verification());
                }
                return;
            }
            if (!result.accepted() || result.verification() == null) {
                plugin.logAuth(player, TrustedAuthProviders.HMAC_BRIDGE, "UNKNOWN",
                        String.valueOf(result.outcome()), "IGNORE", "Packet not accepted as Premium");
                return;
            }
            plugin.logAuth(player, detectorLabel(result.verification().detectorVersion()), "PREMIUM",
                    "ACCEPTED", "AUTO_AUTH", "HMAC-verified Mojang session");
            if (pending == null) {
                plugin.premiumPackets().store(result.verification());
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.workers().database().execute(() -> {
                    PendingPlayer created = plugin.auth().pending(player.getUniqueId());
                    if (created != null && !created.authenticated()) {
                        AuthResult auth = plugin.auth().applyPremium(created, result.verification(), System.currentTimeMillis());
                        plugin.getServer().getScheduler().runTask(plugin, () -> notifyResult(player, auth, TrustedAuthProviders.HMAC_BRIDGE));
                    }
                }), 1L);
                return;
            }
            if (pending.authenticated()) {
                pending.markPremium(result.verification(), TrustedAuthProviders.HMAC_BRIDGE);
                return;
            }
            AuthResult auth = plugin.auth().applyPremium(pending, result.verification(), System.currentTimeMillis());
            plugin.getServer().getScheduler().runTask(plugin, () -> notifyResult(player, auth, TrustedAuthProviders.HMAC_BRIDGE));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Premium bridge handling failed for a player", ex);
        }
    }

    private void notifyResult(Player player, AuthResult auth, String provider) {
        if (!player.isOnline()) {
            return;
        }
        plugin.logAuth(player, provider,
                auth.outcome() == AuthOutcome.PREMIUM_AUTO_LOGIN ? "PREMIUM" : "CRACKED",
                auth.outcome().name(),
                auth.outcome() == AuthOutcome.PREMIUM_NAME_PROTECTED ? "KICK" : (auth.authenticates() ? "AUTO_AUTH" : "CONTINUE"),
                auth.outcome() == AuthOutcome.PREMIUM_NAME_PROTECTED
                        ? "Cracked client used a reserved Premium username"
                        : "Bridge packet applied");
        if (auth.outcome() == AuthOutcome.PREMIUM_NAME_PROTECTED) {
            plugin.sounds().premiumDenied(player);
            player.kick(plugin.messages().component("kick.premium-protected", Map.of()));
            return;
        }
        if (auth.outcome() == AuthOutcome.VERIFICATION_UNAVAILABLE) {
            plugin.sounds().authError(player);
            player.kick(plugin.messages().component("kick.verification-unavailable", Map.of()));
            return;
        }
        if (!auth.authenticates()) {
            return;
        }
        if (auth.outcome() == AuthOutcome.PREMIUM_AUTO_LOGIN && plugin.settings().premium().successMessage()) {
            player.sendMessage(plugin.messages().component("premium.auto-login", Map.of()));
        }
        plugin.sounds().playOutcome(player, auth.outcome());
    }

    private static String detectorLabel(String detectorVersion) {
        if (detectorVersion == null || detectorVersion.isBlank() || "none".equalsIgnoreCase(detectorVersion)) {
            return TrustedAuthProviders.HMAC_BRIDGE;
        }
        return TrustedAuthProviders.CONNECTION_DETECTOR + "/" + detectorVersion;
    }
}
