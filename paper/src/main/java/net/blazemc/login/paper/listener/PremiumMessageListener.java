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
        if (!plugin.settings().premium().bridgeEnabled() || !plugin.settings().premium().autoLoginEnabled()) {
            return;
        }
        plugin.workers().database().execute(() -> handle(player, message));
    }

    private void handle(Player player, byte[] message) {
        try {
            PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
            PremiumVerifier.VerificationResult result = plugin.premiumVerifier().verify(
                    message,
                    plugin.premiumSecret(),
                    plugin.settings().premium(),
                    player.getUniqueId(),
                    player.getName(),
                    System.currentTimeMillis()
            );
            if (!result.accepted()) {
                if (result.outcome() != VerificationOutcome.NOT_PREMIUM) {
                    plugin.getLogger().fine("Rejected premium bridge packet for " + player.getName() + ": " + result.outcome());
                }
                return;
            }
            if (pending == null) {
                // Message arrived before join tracking. Apply after a tick.
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.workers().database().execute(() -> {
                    PendingPlayer created = plugin.auth().pending(player.getUniqueId());
                    if (created != null && !created.authenticated()) {
                        AuthResult auth = plugin.auth().applyPremium(created, result.verification(), System.currentTimeMillis());
                        plugin.getServer().getScheduler().runTask(plugin, () -> notifyPremium(player, auth));
                    }
                }));
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
        if (!player.isOnline() || !auth.authenticates()) {
            return;
        }
        if (plugin.settings().premium().successMessage()) {
            player.sendMessage(plugin.messages().component("premium.auto-login", Map.of()));
        }
    }
}
