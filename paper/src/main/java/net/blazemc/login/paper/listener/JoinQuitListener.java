package net.blazemc.login.paper.listener;

import java.util.Map;
import net.blazemc.login.auth.AuthOutcome;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.ip.AccountLimitService;
import net.blazemc.login.model.AccountType;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.paper.PaperPlayers;
import net.blazemc.login.premium.PremiumVerification;
import net.blazemc.login.storage.AccountRecord;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinQuitListener implements Listener {

    private final BlazeMCLoginPlugin plugin;

    public JoinQuitListener(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip;
        try {
            ip = PaperPlayers.normalizedIp(player);
        } catch (RuntimeException ex) {
            ip = "0.0.0.0";
        }
        AccountLimitService.Decision listed = plugin.auth().limits()
                .checkNewRegistration(ip, AccountType.UNKNOWN, plugin.settings().accountLimits(), false);
        if (listed == AccountLimitService.Decision.BLACKLISTED) {
            player.kick(plugin.messages().component("kick.blacklisted", Map.of()));
            return;
        }
        String ipHash = plugin.ipHasher().hashNormalized(ip);
        PendingPlayer pending = plugin.auth().trackJoin(player.getUniqueId(), player.getName(), ipHash, ip, System.currentTimeMillis());
        Location loc = player.getLocation();
        pending.freezeAt(loc.getWorld() == null ? "world" : loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        if (plugin.settings().loginSpawn().enabled() || plugin.settings().restrictions().teleportToLoginSpawn()) {
            var spawn = plugin.settings().loginSpawn();
            var world = plugin.getServer().getWorld(spawn.world());
            if (world != null) {
                Location destination = new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
                pending.allowNextPluginTeleport();
                pending.freezeAt(spawn.world(), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
                player.teleport(destination);
            }
        }
        plugin.workers().database().execute(() -> {
            PremiumVerification queued = plugin.premiumPackets().take(player.getUniqueId(), System.currentTimeMillis());
            AuthResult result;
            if (queued != null && queued.premium()) {
                result = plugin.auth().applyPremium(pending, queued, System.currentTimeMillis());
            } else {
                if (queued != null) {
                    pending.markCrackedVerdict();
                }
                result = plugin.auth().handleJoin(pending, PaperPlayers.hasBypassLogin(player), System.currentTimeMillis());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> afterJoin(player, pending, result));
        });
    }

    private void afterJoin(Player player, PendingPlayer pending, AuthResult result) {
        if (!player.isOnline()) {
            return;
        }
        if (result.outcome() == AuthOutcome.PREMIUM_NAME_PROTECTED) {
            plugin.sounds().premiumDenied(player);
            player.kick(plugin.messages().component("kick.premium-protected", Map.of()));
            return;
        }
        if (result.outcome() == AuthOutcome.ACCOUNT_LOCKED) {
            player.kick(plugin.messages().component("kick.locked", Map.of()));
            return;
        }
        if (result.authenticates()) {
            if (result.outcome() == AuthOutcome.PREMIUM_AUTO_LOGIN && plugin.settings().premium().successMessage()) {
                player.sendMessage(plugin.messages().component("premium.auto-login", Map.of()));
            }
            plugin.sounds().playOutcome(player, result.outcome());
            return;
        }
        int wait = plugin.settings().premium().autoLoginEnabled() || plugin.settings().premium().protectReservedNames()
                ? Math.max(0, plugin.settings().premium().waitForBridgeMillis())
                : 0;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> promptOrProtect(player, pending), Math.max(1L, wait / 50L));
    }

    private void promptOrProtect(Player player, PendingPlayer pending) {
        if (!player.isOnline() || pending.authenticated()) {
            return;
        }
        if (pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.PREMIUM) {
            return;
        }
        plugin.workers().database().execute(() -> {
            AccountRecord byName = plugin.accounts().findByUsername(player.getName()).orElse(null);
            boolean reserved = byName != null && (byName.accountType() == AccountType.PREMIUM || byName.premiumUuid() != null)
                    && !byName.uuid().equals(player.getUniqueId());
            boolean registered = plugin.accounts().findByUuid(player.getUniqueId()).isPresent()
                    || (byName != null && byName.uuid().equals(player.getUniqueId()));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || pending.authenticated()) {
                    return;
                }
                if (reserved && plugin.settings().premium().protectReservedNames()) {
                    plugin.sounds().premiumDenied(player);
                    player.kick(plugin.messages().component("kick.premium-protected", Map.of()));
                    return;
                }
                if (pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.NONE
                        && plugin.settings().premium().rejectUnverifiedSessions()
                        && plugin.settings().premium().bridgeEnabled()
                        && plugin.premiumSecret().length > 0) {
                    plugin.sounds().authError(player);
                    player.kick(plugin.messages().component("kick.verification-unavailable", Map.of()));
                    return;
                }
                if (registered) {
                    player.sendMessage(plugin.messages().component("login.prompt", Map.of()));
                    plugin.sounds().loginPrompt(player);
                } else {
                    player.sendMessage(plugin.messages().component("register.prompt", Map.of()));
                    plugin.sounds().registerPrompt(player);
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.premiumPackets().discard(event.getPlayer().getUniqueId());
        plugin.auth().untrack(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        plugin.premiumPackets().discard(event.getPlayer().getUniqueId());
        plugin.auth().untrack(event.getPlayer().getUniqueId());
    }
}
