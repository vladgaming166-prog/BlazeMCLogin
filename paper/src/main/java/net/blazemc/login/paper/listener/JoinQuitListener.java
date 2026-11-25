package net.blazemc.login.paper.listener;

import java.util.Map;
import net.blazemc.login.auth.AuthOutcome;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.ip.AccountLimitService;
import net.blazemc.login.model.AccountType;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.paper.PaperPlayers;
import net.blazemc.login.paper.premium.TrustedAuthProviders;
import net.blazemc.login.premium.PremiumVerification;
import net.blazemc.login.premium.PremiumVerificationCodec;
import net.blazemc.login.premium.TrustedAuthStatus;
import net.blazemc.login.storage.AccountRecord;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

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
            plugin.logAuth(player, "none", "UNKNOWN", "none", "KICK", "IP blacklisted");
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
        plugin.logAuth(player, plugin.trustedAuth().detectedPremiumOrCracked(), "PENDING",
                "waiting", "WAIT", "Waiting for Connection Detector / PremiumOrCracked / HMAC bridge");
        beginAuth(player, pending);
    }

    private void beginAuth(Player player, PendingPlayer pending) {
        plugin.workers().database().execute(() -> {
            PremiumVerification queued = plugin.premiumPackets().take(player.getUniqueId(), System.currentTimeMillis());
            if (queued != null && queued.premium()) {
                AuthResult result = plugin.auth().applyPremium(pending, queued, System.currentTimeMillis());
                plugin.getServer().getScheduler().runTask(plugin, () -> afterDecision(player, pending, result, TrustedAuthProviders.HMAC_BRIDGE));
                return;
            }
            if (queued != null) {
                pending.markCrackedVerdict(TrustedAuthProviders.HMAC_BRIDGE);
                AuthResult result = plugin.auth().handleJoin(pending, PaperPlayers.hasBypassLogin(player), System.currentTimeMillis());
                plugin.getServer().getScheduler().runTask(plugin, () -> afterDecision(player, pending, result, TrustedAuthProviders.HMAC_BRIDGE));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                TrustedAuthProviders.Probe probe = plugin.trustedAuth().probe(player);
                if (probe.status() == TrustedAuthStatus.PREMIUM) {
                    applyLocalPremium(player, pending, probe.provider());
                    return;
                }
                if (probe.status() == TrustedAuthStatus.CRACKED) {
                    finalizeCracked(player, pending, probe.provider());
                    return;
                }
                waitForTrustedVerdict(player, pending);
            });
        });
    }

    private void waitForTrustedVerdict(Player player, PendingPlayer pending) {
        int waitMs = Math.max(500, plugin.settings().premium().waitForBridgeMillis());
        int periodTicks = 5;
        int[] remainingTicks = {Math.max(periodTicks, waitMs / 50)};
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || pending.authenticated()) {
                    cancel();
                    return;
                }
                if (pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.PREMIUM) {
                    cancel();
                    return;
                }
                PremiumVerification queued = plugin.premiumPackets().take(player.getUniqueId(), System.currentTimeMillis());
                if (queued != null && queued.premium()) {
                    cancel();
                    applyQueuedPremium(player, pending, queued, TrustedAuthProviders.HMAC_BRIDGE);
                    return;
                }
                if (queued != null) {
                    cancel();
                    finalizeCracked(player, pending, TrustedAuthProviders.HMAC_BRIDGE);
                    return;
                }
                TrustedAuthProviders.Probe probe = plugin.trustedAuth().probe(player);
                if (probe.status() == TrustedAuthStatus.PREMIUM) {
                    cancel();
                    applyLocalPremium(player, pending, probe.provider());
                    return;
                }
                if (probe.status() == TrustedAuthStatus.CRACKED) {
                    cancel();
                    finalizeCracked(player, pending, probe.provider());
                    return;
                }
                remainingTicks[0] -= periodTicks;
                if (remainingTicks[0] <= 0) {
                    cancel();
                    finalizeUnknown(player, pending);
                }
            }
        }.runTaskTimer(plugin, periodTicks, periodTicks);
    }

    private void applyLocalPremium(Player player, PendingPlayer pending, String provider) {
        PremiumVerification verification = PremiumVerificationCodec.create(
                player.getUniqueId(),
                player.getName(),
                true,
                provider
        );
        applyQueuedPremium(player, pending, verification, provider);
    }

    private void applyQueuedPremium(Player player, PendingPlayer pending, PremiumVerification verification, String provider) {
        plugin.workers().database().execute(() -> {
            pending.setAuthProvider(provider);
            AuthResult result = plugin.auth().applyPremium(pending, verification, System.currentTimeMillis());
            plugin.getServer().getScheduler().runTask(plugin, () -> afterDecision(player, pending, result, provider));
        });
    }

    private void finalizeCracked(Player player, PendingPlayer pending, String provider) {
        plugin.workers().database().execute(() -> {
            pending.markCrackedVerdict(provider);
            AuthResult result = plugin.auth().handleJoin(pending, PaperPlayers.hasBypassLogin(player), System.currentTimeMillis());
            plugin.getServer().getScheduler().runTask(plugin, () -> afterDecision(player, pending, result, provider));
        });
    }

    private void finalizeUnknown(Player player, PendingPlayer pending) {
        if (!player.isOnline() || pending.authenticated()) {
            return;
        }
        plugin.workers().database().execute(() -> {
            boolean reserved = plugin.auth().isReservedPremiumName(player.getName(), player.getUniqueId());
            AccountRecord own = plugin.accounts().findByUuid(player.getUniqueId()).orElse(null);
            boolean ownPremium = own != null && (own.accountType() == AccountType.PREMIUM || own.premiumUuid() != null);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || pending.authenticated()) {
                    return;
                }
                if (pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.PREMIUM) {
                    return;
                }
                if (ownPremium || reserved) {
                    plugin.logAuth(player, pending.authProvider(), "UNVERIFIED",
                            "none", "KICK", "Premium identity could not be verified; fail-closed");
                    plugin.sounds().authError(player);
                    player.kick(plugin.messages().component("kick.verification-unavailable", Map.of()));
                    return;
                }
                AuthResult result = plugin.auth().handleJoin(pending, PaperPlayers.hasBypassLogin(player), System.currentTimeMillis());
                afterDecision(player, pending, result, "timeout-unverified");
            });
        });
    }

    private void afterDecision(Player player, PendingPlayer pending, AuthResult result, String provider) {
        if (!player.isOnline()) {
            return;
        }
        plugin.logAuth(player, provider, pending.bridgeVerdict().name(),
                result.outcome().name(), decisionLabel(result), reason(result));
        if (result.outcome() == AuthOutcome.ALREADY_AUTHENTICATED) {
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
        if (result.outcome() == AuthOutcome.VERIFICATION_UNAVAILABLE) {
            plugin.sounds().authError(player);
            player.kick(plugin.messages().component("kick.verification-unavailable", Map.of()));
            return;
        }
        if (result.authenticates()) {
            if (result.outcome() == AuthOutcome.PREMIUM_AUTO_LOGIN && plugin.settings().premium().successMessage()) {
                player.sendMessage(plugin.messages().component("premium.auto-login", Map.of()));
            }
            plugin.sounds().playOutcome(player, result.outcome());
            return;
        }
        if (result.outcome() == AuthOutcome.AWAITING_VERIFICATION) {
            return;
        }
        promptCracked(player, pending);
    }

    private void promptCracked(Player player, PendingPlayer pending) {
        if (!player.isOnline() || pending.authenticated()) {
            return;
        }
        if (pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.PREMIUM) {
            return;
        }
        plugin.workers().database().execute(() -> {
            AccountRecord byName = plugin.accounts().findByUsername(player.getName()).orElse(null);
            boolean reserved = plugin.auth().isReservedPremiumName(player.getName(), player.getUniqueId());
            boolean registered = plugin.accounts().findByUuid(player.getUniqueId()).isPresent()
                    || (byName != null && byName.uuid().equals(player.getUniqueId()));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || pending.authenticated()) {
                    return;
                }
                if (pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.PREMIUM) {
                    return;
                }
                if (reserved && pending.bridgeVerdict() == PendingPlayer.BridgeVerdict.CRACKED
                        && plugin.settings().premium().protectReservedNames()) {
                    plugin.logAuth(player, pending.authProvider(), "CRACKED",
                            "PREMIUM_NAME_PROTECTED", "KICK", "Cracked session used a reserved Premium username");
                    plugin.sounds().premiumDenied(player);
                    player.kick(plugin.messages().component("kick.premium-protected", Map.of()));
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

    private static String decisionLabel(AuthResult result) {
        if (result.outcome() == AuthOutcome.PREMIUM_AUTO_LOGIN) {
            return "AUTO_AUTH";
        }
        if (result.outcome() == AuthOutcome.PREMIUM_NAME_PROTECTED
                || result.outcome() == AuthOutcome.VERIFICATION_UNAVAILABLE
                || result.outcome() == AuthOutcome.ACCOUNT_LOCKED) {
            return "KICK";
        }
        if (result.authenticates()) {
            return "AUTHENTICATED";
        }
        return "CRACKED_PROMPT";
    }

    private static String reason(AuthResult result) {
        return switch (result.outcome()) {
            case PREMIUM_AUTO_LOGIN -> "Verified Premium session; password login skipped";
            case PREMIUM_NAME_PROTECTED -> "Cracked client used a reserved Premium username";
            case VERIFICATION_UNAVAILABLE -> "Trusted provider did not confirm identity";
            case SESSION_RESTORED -> "Cracked session restored";
            case NOT_AUTHENTICATED -> "Cracked login or registration required";
            case BYPASS -> "Permission bypass";
            default -> result.outcome().name();
        };
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
