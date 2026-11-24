package net.blazemc.login.paper.listener;

import java.util.Map;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.ip.AccountLimitService;
import net.blazemc.login.model.AccountType;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.paper.PaperPlayers;
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
        if (plugin.auth().settings().accountLimits().blacklist() != null) {
            AccountLimitService.Decision listed = new AccountLimitService(plugin.accounts(), plugin.ipHasher())
                    .checkNewRegistration(ip, AccountType.UNKNOWN, plugin.settings().accountLimits(), false);
            if (listed == AccountLimitService.Decision.BLACKLISTED) {
                player.kick(plugin.messages().component("kick.blacklisted", Map.of()));
                return;
            }
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
                player.teleport(destination);
                pending.freezeAt(spawn.world(), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            }
        }
        plugin.workers().database().execute(() -> {
            AuthResult result = plugin.auth().handleJoin(pending, PaperPlayers.hasBypassLogin(player), System.currentTimeMillis());
            plugin.getServer().getScheduler().runTask(plugin, () -> afterJoin(player, pending, result));
        });
    }

    private void afterJoin(Player player, PendingPlayer pending, AuthResult result) {
        if (!player.isOnline()) {
            return;
        }
        if (result.outcome() == net.blazemc.login.auth.AuthOutcome.ACCOUNT_LOCKED) {
            player.kick(plugin.messages().component("kick.locked", Map.of()));
            return;
        }
        if (pending.authenticated()) {
            return;
        }
        int wait = plugin.settings().premium().autoLoginEnabled()
                ? Math.max(0, plugin.settings().premium().waitForBridgeMillis())
                : 0;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || pending.authenticated()) {
                return;
            }
            boolean registered = plugin.accounts().findByUuid(player.getUniqueId()).isPresent()
                    || plugin.accounts().findByUsername(player.getName()).isPresent();
            player.sendMessage(plugin.messages().component(registered ? "login.prompt" : "register.prompt", Map.of()));
        }, Math.max(1L, wait / 50L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.auth().untrack(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        plugin.auth().untrack(event.getPlayer().getUniqueId());
    }
}
