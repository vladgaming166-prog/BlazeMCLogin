package net.blazemc.login.paper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.blazemc.login.PluginConstants;
import net.blazemc.login.auth.AuthService;
import net.blazemc.login.config.ConfigLoader;
import net.blazemc.login.config.PluginSettings;
import net.blazemc.login.config.SecretYaml;
import net.blazemc.login.ip.AccountLimitService;
import net.blazemc.login.ip.IpHasher;
import net.blazemc.login.message.MessageCatalog;
import net.blazemc.login.model.LanguageCode;
import net.blazemc.login.paper.command.AdminCommand;
import net.blazemc.login.paper.command.PlayerCommands;
import net.blazemc.login.paper.listener.JoinQuitListener;
import net.blazemc.login.paper.listener.PremiumMessageListener;
import net.blazemc.login.paper.listener.RestrictionListener;
import net.blazemc.login.paper.premium.TrustedAuthProviders;
import net.blazemc.login.paper.sound.AuthSoundService;
import net.blazemc.login.premium.PremiumPacketBuffer;
import net.blazemc.login.premium.PremiumVerifier;
import net.blazemc.login.security.Argon2PasswordHasher;
import net.blazemc.login.security.ForwardingSecretLocator;
import net.blazemc.login.security.PasswordPolicy;
import net.blazemc.login.security.SecureTokens;
import net.blazemc.login.session.SessionService;
import net.blazemc.login.storage.AccountRepository;
import net.blazemc.login.storage.BackupService;
import net.blazemc.login.storage.DatabaseManager;
import net.blazemc.login.storage.SessionRepository;
import net.blazemc.login.util.AsyncWorkers;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlazeMCLoginPlugin extends JavaPlugin {

    private PluginSettings settings;
    private MessageCatalog messages;
    private DatabaseManager database;
    private AuthService auth;
    private AsyncWorkers workers;
    private PremiumVerifier premiumVerifier;
    private byte[] premiumSecret;
    private IpHasher ipHasher;
    private BackupService backups;
    private AccountRepository accounts;
    private AuthSoundService sounds;
    private PremiumPacketBuffer premiumPackets;
    private TrustedAuthProviders trustedAuth;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfigs();
            reloadRuntime(false);
            getServer().getMessenger().registerIncomingPluginChannel(this, PluginConstants.CHANNEL_ID, new PremiumMessageListener(this));
            getServer().getMessenger().registerOutgoingPluginChannel(this, PluginConstants.CHANNEL_ID);
            getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
            getServer().getPluginManager().registerEvents(new RestrictionListener(this), this);
            PlayerCommands playerCommands = new PlayerCommands(this);
            bind("login", playerCommands);
            bind("register", playerCommands);
            bind("logout", playerCommands);
            bind("changepassword", playerCommands);
            AdminCommand admin = new AdminCommand(this);
            bind("bmlogin", admin);
            getServer().getScheduler().runTaskTimer(this, this::tickTimeouts, 20L, 20L);
            getLogger().info("BlazeMCLogin " + PluginConstants.VERSION + " enabled (" + settings.language().code() + ")");
            getLogger().info("Auth providers: HMAC-bridge=" + (premiumSecret.length > 0 ? "ready" : "no-secret")
                    + " PremiumOrCracked=" + trustedAuth.detectedPremiumOrCracked()
                    + " PlaceholderAPI=" + (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null));
        } catch (RuntimeException | Error ex) {
            getLogger().log(Level.SEVERE, "BlazeMCLogin failed to enable", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (workers != null) {
            workers.close();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadRuntime(boolean keepDatabase) {
        Path data = getDataFolder().toPath();
        ensureSecrets(data);
        this.settings = new ConfigLoader().load(data);
        this.messages = new MessageCatalog();
        this.messages.load(data, settings.language(), getClass().getClassLoader());
        if (!keepDatabase || database == null || !database.isOpen()) {
            if (database != null) {
                database.close();
            }
            this.database = new DatabaseManager(settings.database(), data, getLogger());
            this.database.open();
        }
        this.accounts = new AccountRepository(database);
        SessionRepository sessionRepository = new SessionRepository(database);
        this.ipHasher = new IpHasher(parseSecret(settings.security().ipHmacSecretHex(), "ip"));
        String sessionSecret = settings.security().premiumHmacSecretHex().isBlank()
                ? settings.security().ipHmacSecretHex()
                : settings.security().premiumHmacSecretHex();
        SessionService sessionService = new SessionService(sessionRepository, parseSecret(sessionSecret, "session"));
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(
                settings.security().argonMemoryKib(),
                settings.security().argonIterations(),
                settings.security().argonParallelism()
        );
        AccountLimitService limits = new AccountLimitService(accounts, ipHasher);
        this.auth = new AuthService(accounts, hasher, new PasswordPolicy(settings.security()), limits, sessionService, settings);
        this.premiumSecret = ForwardingSecretLocator.resolve(
                settings.premium().sharedSecretHex(),
                data,
                getServer().getWorldContainer().toPath()
        );
        this.premiumVerifier = new PremiumVerifier(Math.max(15, settings.premium().maxMessageAgeSeconds()) * 2000L);
        this.premiumPackets = new PremiumPacketBuffer(Math.max(5_000L, settings.premium().waitForBridgeMillis() * 4L));
        this.sounds = new AuthSoundService(this);
        this.trustedAuth = new TrustedAuthProviders(this);
        this.backups = new BackupService(database);
        if (workers != null) {
            workers.close();
        }
        this.workers = new AsyncWorkers(settings.general().asyncThreads());
        if (premiumSecret.length == 0 && settings.premium().autoLoginEnabled()) {
            getLogger().warning("Premium auto-login is enabled but no HMAC secret was found. The Velocity forwarding secret or premium.yml shared-secret is required. Values are not logged.");
        }
    }

    public boolean setLanguage(LanguageCode code) {
        Path config = getDataFolder().toPath().resolve("config.yml");
        try {
            String yaml = Files.readString(config, StandardCharsets.UTF_8);
            if (yaml.contains("language:")) {
                yaml = yaml.replaceAll("(?m)^language:.*$", "language: " + code.code());
            } else {
                yaml = "language: " + code.code() + "\n" + yaml;
            }
            Files.writeString(config, yaml, StandardCharsets.UTF_8);
            reloadRuntime(true);
            return true;
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Unable to write language setting", ex);
            return false;
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public MessageCatalog messages() {
        return messages;
    }

    public AuthService auth() {
        return auth;
    }

    public AsyncWorkers workers() {
        return workers;
    }

    public PremiumVerifier premiumVerifier() {
        return premiumVerifier;
    }

    public byte[] premiumSecret() {
        return premiumSecret;
    }

    public IpHasher ipHasher() {
        return ipHasher;
    }

    public BackupService backups() {
        return backups;
    }

    public DatabaseManager database() {
        return database;
    }

    public AccountRepository accounts() {
        return accounts;
    }

    public AuthSoundService sounds() {
        return sounds;
    }

    public PremiumPacketBuffer premiumPackets() {
        return premiumPackets;
    }

    public TrustedAuthProviders trustedAuth() {
        if (trustedAuth == null) {
            trustedAuth = new TrustedAuthProviders(this);
        }
        return trustedAuth;
    }

    public void logAuth(org.bukkit.entity.Player player, String provider, String type, String verification, String decision, String reason) {
        String line = "[auth] player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " provider=" + provider
                + " type=" + type
                + " verification=" + verification
                + " decision=" + decision
                + " reason=" + reason;
        if (settings != null && settings.general().debug()) {
            getLogger().info(line);
            return;
        }
        if ("KICK".equals(decision) || "AUTO_AUTH".equals(decision) || "WAIT".equals(decision)) {
            getLogger().info(line);
        } else {
            getLogger().fine(line);
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command not defined in plugin.yml: " + name);
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor commandExecutor) {
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void tickTimeouts() {
        int timeout = settings.security().loginTimeoutSeconds();
        long now = System.currentTimeMillis();
        for (org.bukkit.entity.Player player : List.copyOf(getServer().getOnlinePlayers())) {
            var pending = auth.pending(player.getUniqueId());
            if (pending != null && pending.timedOut(now, timeout)) {
                player.kick(messages.component("kick.timeout", Map.of()));
            }
        }
    }

    private void saveDefaultConfigs() {
        getDataFolder().mkdirs();
        Path data = getDataFolder().toPath();
        copyIfMissing("config.yml");
        copyIfMissing("messages.yml");
        copyIfMissing("security.yml");
        copyIfMissing("database.yml");
        copyIfMissing("premium.yml");
        copyIfMissing("sessions.yml");
        copyIfMissing("account-limits.yml");
        copyIfMissing("login-spawn.yml");
        copyIfMissing("login-restrictions.yml");
        copyIfMissing("sounds.yml");
        try {
            Files.createDirectories(data.resolve("lang"));
            Files.createDirectories(data.resolve("backups"));
            Files.createDirectories(data.resolve("database"));
            for (LanguageCode code : LanguageCode.values()) {
                Path dest = data.resolve("lang").resolve(code.resourceFile());
                if (!Files.exists(dest)) {
                    try (InputStream in = getResource("lang/" + code.resourceFile())) {
                        if (in != null) {
                            Files.copy(in, dest);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create plugin directories", ex);
        }
    }

    private void copyIfMissing(String name) {
        Path dest = getDataFolder().toPath().resolve(name);
        if (Files.exists(dest)) {
            return;
        }
        saveResource(name, false);
    }

    private void ensureSecrets(Path data) {
        fillSecrets(data.resolve("security.yml"), "ip-hmac:", "session-hmac:");
        // premium shared-secret stays empty on purpose so both sides can use the
        // Velocity modern-forwarding secret already shared by the network.
    }

    private void fillSecrets(Path file, String... keys) {
        try {
            if (!Files.exists(file)) {
                return;
            }
            String yaml = Files.readString(file, StandardCharsets.UTF_8);
            String filled = yaml;
            for (String key : keys) {
                filled = SecretYaml.fill(filled, key, SecureTokens::randomSecret);
            }
            if (!filled.equals(yaml)) {
                Files.writeString(file, filled, StandardCharsets.UTF_8);
                getLogger().info("Generated a new secret in " + file.getFileName() + ". The value is not logged.");
            }
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "Unable to persist generated secrets in " + file.getFileName(), ex);
        }
    }

    private static byte[] parseSecret(String hex, String name) {
        if (hex == null || hex.isBlank() || "CHANGE_ME".equalsIgnoreCase(hex)) {
            return SecureTokens.randomBytes(32);
        }
        try {
            String value = hex.trim().toLowerCase(Locale.ROOT);
            if ((value.length() & 1) == 0 && value.matches("[0-9a-f]+")) {
                return HexFormat.of().parseHex(value);
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return hex.getBytes(StandardCharsets.UTF_8);
    }
}
