package net.blazemc.login.paper.listener;

import java.util.Map;
import java.util.logging.Level;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
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
                plugin.getLogger().fine("Rejected premium bridge packet for " + player.getName() + ": " + result.outcome());
                return;
            }
            PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
            if (result.outcome() == VerificationOutcome.CRACKED_SESSION) {
                if (pending != null) {
                    pending.markCrackedVerdict();
                } else if (result.verification() != null) {
                    plugin.premiumPackets().store(result.verification());
                }
                return;
            }
            if (!result.accepted() || result.verification() == null) {
                return;
            }
            if (pending == null) {
                plugin.premiumPackets().store(result.verification());
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.workers().database().execute(() -> {
                    PendingPlayer created = plugin.auth().pending(player.getUniqueId());
                    if (created != null && !created.authenticated()) {
                        AuthResult auth = plugin.auth().applyPremium(created, result.verification(), System.currentTimeMillis());
                        plugin.getServer().getScheduler().runTask(plugin, () -> notifyPremium(player, auth));
                    }
                }), 1L);
                return;
            }
            if (pending.authenticated()) {
                pending.markPremium(result.verification());
                return;
            }
            AuthResult auth = plugin.auth().applyPremium(pending, result.verification(), System.currentTimeMillis());
            plugin.getServer().getScheduler().runTask(plugin, () -> notifyPremium(player, auth));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Premium bridge handling failed for a player", ex);
        }
    }

    private void notifyPremium(Player player, AuthResult auth) {
        if (!player.isOnline()) {
            return;
        }
        if (auth.outcome() == net.blazemc.login.auth.AuthOutcome.PREMIUM_NAME_PROTECTED) {
            plugin.sounds().premiumDenied(player);
            player.kick(plugin.messages().component("kick.premium-protected", Map.of()));
            return;
        }
        if (!auth.authenticates()) {
            return;
        }
        if (plugin.settings().premium().successMessage()) {
            player.sendMessage(plugin.messages().component("premium.auto-login", Map.of()));
        }
        plugin.sounds().playOutcome(player, auth.outcome());
    }
}
