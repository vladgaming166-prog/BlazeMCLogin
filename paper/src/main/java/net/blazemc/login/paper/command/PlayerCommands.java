package net.blazemc.login.paper.command;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.blazemc.login.auth.AuthOutcome;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.auth.PendingPlayer;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.paper.PaperPlayers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerCommands implements CommandExecutor, TabCompleter {

    private final BlazeMCLoginPlugin plugin;

    public PlayerCommands(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().component("error.player-only", Map.of()));
            return true;
        }
        PendingPlayer pending = plugin.auth().pending(player.getUniqueId());
        if (pending == null) {
            pending = plugin.auth().trackJoin(
                    player.getUniqueId(),
                    player.getName(),
                    plugin.ipHasher().hashNormalized(PaperPlayers.normalizedIp(player)),
                    PaperPlayers.normalizedIp(player),
                    System.currentTimeMillis()
            );
        }
        String name = command.getName().toLowerCase();
        switch (name) {
            case "register" -> register(player, pending, args);
            case "login" -> login(player, pending, args);
            case "logout" -> logout(player, pending);
            case "changepassword" -> changePassword(player, pending, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void register(Player player, PendingPlayer pending, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().component("register.usage", Map.of()));
            return;
        }
        String password = args[0];
        String confirm = args[1];
        runAsync(player, () -> plugin.auth().register(pending, password, confirm, PaperPlayers.hasBypassIp(player), System.currentTimeMillis()));
    }

    private void login(Player player, PendingPlayer pending, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.messages().component("login.usage", Map.of()));
            return;
        }
        String password = args[0];
        runAsync(player, () -> plugin.auth().login(pending, password, System.currentTimeMillis()));
    }

    private void logout(Player player, PendingPlayer pending) {
        runAsync(player, () -> plugin.auth().logout(pending));
    }

    private void changePassword(Player player, PendingPlayer pending, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().component("changepassword.usage", Map.of()));
            return;
        }
        String oldPassword = args[0];
        String newPassword = args[1];
        runAsync(player, () -> plugin.auth().changePassword(pending, oldPassword, newPassword, System.currentTimeMillis()));
    }

    private void runAsync(Player player, java.util.function.Supplier<AuthResult> task) {
        CompletableFuture.supplyAsync(task, plugin.workers().hashing())
                .whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().warning("Authentication command failed");
                        player.sendMessage(plugin.messages().component("error.database", Map.of()));
                        return;
                    }
                    player.sendMessage(plugin.messages().component(messageKey(result.outcome()), Map.of()));
                }));
    }

    static String messageKey(AuthOutcome outcome) {
        return switch (outcome) {
            case REGISTERED -> "register.success";
            case LOGGED_IN, SESSION_RESTORED, BYPASS, PREMIUM_AUTO_LOGIN -> "login.success";
            case LOGGED_OUT -> "logout.success";
            case PASSWORD_CHANGED -> "changepassword.success";
            case ALREADY_AUTHENTICATED -> "login.already";
            case ALREADY_REGISTERED -> "register.already";
            case NOT_REGISTERED -> "login.not-registered";
            case WRONG_PASSWORD -> "login.wrong-password";
            case PASSWORD_EMPTY -> "register.empty";
            case PASSWORD_TOO_SHORT -> "register.too-short";
            case PASSWORD_TOO_LONG -> "register.too-long";
            case PASSWORD_TOO_COMMON -> "register.too-common";
            case PASSWORD_COMPLEXITY -> "register.complexity";
            case PASSWORD_MISMATCH -> "register.mismatch";
            case ACCOUNT_LOCKED -> "login.locked";
            case RATE_LIMITED -> "login.rate-limited";
            case IP_LIMIT -> "register.ip-limit";
            case IP_BLACKLISTED -> "register.ip-blacklisted";
            case USERNAME_TAKEN -> "register.already";
            case NOT_AUTHENTICATED -> "logout.not-logged-in";
            case DATABASE_ERROR -> "error.database";
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
