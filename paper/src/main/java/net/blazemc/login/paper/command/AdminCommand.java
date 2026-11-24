package net.blazemc.login.paper.command;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.auth.AuthOutcome;
import net.blazemc.login.auth.AuthResult;
import net.blazemc.login.ip.IpAddressNormalizer;
import net.blazemc.login.migrate.LimboAuthMigrator;
import net.blazemc.login.model.LanguageCode;
import net.blazemc.login.paper.BlazeMCLoginPlugin;
import net.blazemc.login.storage.AccountRecord;
import net.blazemc.login.storage.SessionRecord;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "reload", "status", "info", "accounts", "unregister", "resetpassword",
            "lock", "unlock", "session", "invalidate-session", "backup", "migrate", "language"
    );

    private final BlazeMCLoginPlugin plugin;

    public AdminCommand(BlazeMCLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.messages().component("admin.help-header", Map.of()));
            sender.sendMessage(plugin.messages().component("admin.usage", Map.of()));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!hasPermission(sender, sub)) {
            sender.sendMessage(plugin.messages().component("error.no-permission", Map.of()));
            return true;
        }
        switch (sub) {
            case "reload" -> {
                plugin.reloadRuntime(true);
                sender.sendMessage(plugin.messages().component("admin.reload", Map.of()));
            }
            case "status" -> status(sender);
            case "info" -> info(sender, args);
            case "accounts" -> accounts(sender, args);
            case "unregister" -> mutate(sender, args, uuid -> plugin.auth().unregister(uuid), "admin.unregister");
            case "resetpassword" -> mutate(sender, args, uuid -> plugin.auth().resetPassword(uuid), "admin.resetpassword");
            case "lock" -> mutate(sender, args, uuid -> plugin.auth().setLocked(uuid, true), "admin.lock");
            case "unlock" -> mutate(sender, args, uuid -> plugin.auth().setLocked(uuid, false), "admin.unlock");
            case "session" -> session(sender, args);
            case "invalidate-session" -> invalidate(sender, args);
            case "backup" -> backup(sender);
            case "migrate" -> migrate(sender, args);
            case "language" -> language(sender, args);
            default -> sender.sendMessage(plugin.messages().component("admin.unknown", Map.of()));
        }
        return true;
    }

    private boolean hasPermission(CommandSender sender, String sub) {
        if (sender.hasPermission(PluginConstants.PERM_ADMIN)) {
            return true;
        }
        return switch (sub) {
            case "reload", "language" -> sender.hasPermission(PluginConstants.PERM_RELOAD) || sender.hasPermission(PluginConstants.PERM_LANGUAGE);
            case "status", "info" -> sender.hasPermission(PluginConstants.PERM_INFO);
            case "accounts" -> sender.hasPermission(PluginConstants.PERM_ACCOUNTS);
            case "unregister" -> sender.hasPermission(PluginConstants.PERM_UNREGISTER);
            case "resetpassword" -> sender.hasPermission(PluginConstants.PERM_RESETPASSWORD);
            case "lock", "unlock" -> sender.hasPermission(PluginConstants.PERM_LOCK);
            case "session", "invalidate-session" -> sender.hasPermission(PluginConstants.PERM_SESSION);
            case "backup" -> sender.hasPermission(PluginConstants.PERM_BACKUP);
            case "migrate" -> sender.hasPermission(PluginConstants.PERM_MIGRATE);
            default -> false;
        };
    }

    private void status(CommandSender sender) {
        long authed = plugin.getServer().getOnlinePlayers().stream().filter(p -> plugin.auth().isAuthenticated(p.getUniqueId())).count();
        sender.sendMessage(plugin.messages().component("admin.status", Map.of(
                "version", PluginConstants.VERSION,
                "authed", String.valueOf(authed),
                "online", String.valueOf(plugin.getServer().getOnlinePlayers().size()),
                "accounts", String.valueOf(plugin.accounts().countAll()),
                "database", plugin.settings().database().type().name(),
                "language", plugin.settings().language().code(),
                "bridge", plugin.premiumSecret().length == 0 ? "unconfigured" : "ready"
        )));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("admin.usage", Map.of()));
            return;
        }
        Optional<AccountRecord> account = plugin.auth().find(args[1]);
        if (account.isEmpty()) {
            sender.sendMessage(plugin.messages().component("error.player-not-found", Map.of()));
            return;
        }
        AccountRecord record = account.get();
        sender.sendMessage(plugin.messages().component("admin.info", Map.of(
                "name", record.username(),
                "uuid", record.uuid().toString(),
                "type", record.accountType().name(),
                "status", record.status().name(),
                "registered", Instant.ofEpochMilli(record.registeredAt()).toString(),
                "last-login", Instant.ofEpochMilli(record.lastLoginAt()).toString(),
                "password-version", String.valueOf(record.passwordVersion())
        )));
    }

    private void accounts(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("admin.usage", Map.of()));
            return;
        }
        try {
            String normalized = IpAddressNormalizer.normalize(args[1]);
            String hash = plugin.ipHasher().hashNormalized(normalized);
            List<AccountRecord> list = plugin.accounts().findByIpHash(hash);
            sender.sendMessage(plugin.messages().component("admin.accounts", Map.of("hash", hash.substring(0, Math.min(12, hash.length())) + "…")));
            if (list.isEmpty()) {
                sender.sendMessage(plugin.messages().component("admin.accounts-empty", Map.of()));
                return;
            }
            for (AccountRecord record : list) {
                sender.sendMessage(plugin.messages().component("admin.accounts-line", Map.of(
                        "name", record.username(),
                        "type", record.accountType().name(),
                        "uuid", record.uuid().toString()
                )));
            }
        } catch (RuntimeException ex) {
            sender.sendMessage(plugin.messages().component("error.invalid-ip", Map.of()));
        }
    }

    private void mutate(CommandSender sender, String[] args, java.util.function.Function<UUID, AuthResult> action, String successKey) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("admin.usage", Map.of()));
            return;
        }
        Optional<AccountRecord> account = plugin.auth().find(args[1]);
        if (account.isEmpty()) {
            sender.sendMessage(plugin.messages().component("error.player-not-found", Map.of()));
            return;
        }
        plugin.workers().database().execute(() -> {
            AuthResult result = action.apply(account.get().uuid());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.outcome() == AuthOutcome.NOT_REGISTERED) {
                    sender.sendMessage(plugin.messages().component("error.player-not-found", Map.of()));
                } else {
                    sender.sendMessage(plugin.messages().component(successKey, Map.of("name", account.get().username())));
                    Player online = Bukkit.getPlayer(account.get().uuid());
                    if (online != null && (successKey.equals("admin.unregister") || successKey.equals("admin.resetpassword") || successKey.equals("admin.lock"))) {
                        online.kick(plugin.messages().component("kick.locked", Map.of()));
                    }
                }
            });
        });
    }

    private void session(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("admin.usage", Map.of()));
            return;
        }
        Optional<AccountRecord> account = plugin.auth().find(args[1]);
        if (account.isEmpty()) {
            sender.sendMessage(plugin.messages().component("error.player-not-found", Map.of()));
            return;
        }
        Optional<SessionRecord> session = plugin.auth().sessions().restore(
                account.get(), account.get().ipHash(), plugin.settings().sessions(), System.currentTimeMillis());
        if (session.isEmpty()) {
            sender.sendMessage(plugin.messages().component("admin.session-none", Map.of("name", account.get().username())));
            return;
        }
        SessionRecord record = session.get();
        sender.sendMessage(plugin.messages().component("admin.session", Map.of(
                "name", account.get().username(),
                "expires", Instant.ofEpochMilli(record.expiresAt()).toString(),
                "ip", record.ipHash() == null ? "-" : record.ipHash().substring(0, 12) + "…",
                "version", String.valueOf(record.passwordVersion())
        )));
    }

    private void invalidate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("admin.usage", Map.of()));
            return;
        }
        Optional<AccountRecord> account = plugin.auth().find(args[1]);
        if (account.isEmpty()) {
            sender.sendMessage(plugin.messages().component("error.player-not-found", Map.of()));
            return;
        }
        plugin.auth().sessions().invalidate(account.get().uuid());
        sender.sendMessage(plugin.messages().component("admin.invalidate-session", Map.of("name", account.get().username())));
    }

    private void backup(CommandSender sender) {
        plugin.workers().database().execute(() -> {
            try {
                Path file = plugin.backups().backup();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.messages().component("admin.backup", Map.of("file", file.getFileName().toString()))));
            } catch (Exception ex) {
                plugin.getLogger().warning("Backup failed");
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.messages().component("error.database", Map.of())));
            }
        });
    }

    private void migrate(CommandSender sender, String[] args) {
        Path source = args.length >= 2 ? Path.of(args[1]) : plugin.getDataFolder().toPath().getParent().resolve("limboauth").resolve("limboauth.sqlite");
        boolean overwrite = args.length >= 3 && "overwrite".equalsIgnoreCase(args[2]);
        plugin.workers().database().execute(() -> {
            try {
                LimboAuthMigrator.Report report = new LimboAuthMigrator(plugin.accounts(), plugin.backups()).migrate(source, overwrite);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(plugin.messages().component("admin.migrate", Map.of(
                            "imported", String.valueOf(report.imported()),
                            "duplicates", String.valueOf(report.skippedDuplicate()),
                            "incompatible", String.valueOf(report.skippedIncompatibleHash())
                    )));
                    sender.sendMessage(plugin.messages().componentRaw("&7" + report.notes().stream().findFirst().orElse("")));
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Migration failed: " + ex.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.messages().component("error.database", Map.of())));
            }
        });
    }

    private void language(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().component("admin.language-invalid", Map.of()));
            return;
        }
        LanguageCode code = LanguageCode.parse(args[1]);
        if (!code.code().equals(args[1].trim().toLowerCase(Locale.ROOT))
                && !args[1].equalsIgnoreCase(code.name())
                && !args[1].equalsIgnoreCase("romanian")
                && !args[1].equalsIgnoreCase("english")
                && !args[1].equalsIgnoreCase("romana")) {
            boolean known = Stream.of("ro", "en", "ro-ascii", "romanian", "english", "romana", "roascii")
                    .anyMatch(v -> v.equalsIgnoreCase(args[1]));
            if (!known) {
                sender.sendMessage(plugin.messages().component("admin.language-invalid", Map.of()));
                return;
            }
        }
        if (plugin.setLanguage(code)) {
            sender.sendMessage(plugin.messages().component("admin.language", Map.of("language", code.code() + " (" + code.displayName() + ")")));
        } else {
            sender.sendMessage(plugin.messages().component("error.database", Map.of()));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (List.of("info", "unregister", "resetpassword", "lock", "unlock", "session", "invalidate-session").contains(sub)) {
                return filter(onlineNames(), args[1]);
            }
            if ("language".equals(sub)) {
                return filter(List.of("ro", "en", "ro-ascii"), args[1]);
            }
            if ("accounts".equals(sub) || "migrate".equals(sub)) {
                return List.of();
            }
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
