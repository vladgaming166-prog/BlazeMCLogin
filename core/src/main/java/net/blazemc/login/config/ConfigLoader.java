package net.blazemc.login.config;

import java.nio.file.Path;
import java.util.Map;
import net.blazemc.login.model.LanguageCode;

public final class ConfigLoader {

    public PluginSettings load(Path dataFolder) {
        Map<String, Object> general = YamlSupport.load(dataFolder.resolve("config.yml"));
        Map<String, Object> security = YamlSupport.load(dataFolder.resolve("security.yml"));
        Map<String, Object> premium = YamlSupport.load(dataFolder.resolve("premium.yml"));
        Map<String, Object> sessions = YamlSupport.load(dataFolder.resolve("sessions.yml"));
        Map<String, Object> limits = YamlSupport.load(dataFolder.resolve("account-limits.yml"));
        Map<String, Object> restrictions = YamlSupport.load(dataFolder.resolve("login-restrictions.yml"));
        Map<String, Object> database = YamlSupport.load(dataFolder.resolve("database.yml"));
        Map<String, Object> spawn = YamlSupport.load(dataFolder.resolve("login-spawn.yml"));
        return fromMaps(general, security, premium, sessions, limits, restrictions, database, spawn);
    }

    public PluginSettings fromMaps(
            Map<String, Object> general,
            Map<String, Object> security,
            Map<String, Object> premium,
            Map<String, Object> sessions,
            Map<String, Object> limits,
            Map<String, Object> restrictions,
            Map<String, Object> database,
            Map<String, Object> spawn
    ) {
        GeneralSettings generalSettings = new GeneralSettings(
                LanguageCode.parse(YamlSupport.string(general, "language", "ro")),
                YamlSupport.bool(general, "debug", false),
                YamlSupport.bool(general, "use-proxy-forwarded-address", true),
                YamlSupport.integer(general, "async-threads", 4)
        );
        SecuritySettings securitySettings = new SecuritySettings(
                YamlSupport.integer(security, "security.max-login-attempts", 5),
                YamlSupport.integer(security, "security.lock-duration-seconds", 300),
                YamlSupport.integer(security, "security.login-timeout-seconds", 60),
                YamlSupport.integer(security, "security.minimum-password-length", 8),
                YamlSupport.integer(security, "security.maximum-password-length", 64),
                YamlSupport.bool(security, "security.reject-common-passwords", true),
                YamlSupport.bool(security, "security.require-complexity", false),
                YamlSupport.bool(security, "security.require-uppercase", false),
                YamlSupport.bool(security, "security.require-special-character", false),
                YamlSupport.bool(security, "security.rate-limit.enabled", true),
                YamlSupport.integer(security, "security.rate-limit.attempts-per-minute", 10),
                YamlSupport.string(security, "security.password-hashing.algorithm", "ARGON2ID"),
                YamlSupport.integer(security, "security.password-hashing.memory-kib", 65536),
                YamlSupport.integer(security, "security.password-hashing.iterations", 3),
                YamlSupport.integer(security, "security.password-hashing.parallelism", 1),
                YamlSupport.string(security, "security.secrets.ip-hmac", ""),
                YamlSupport.string(security, "security.secrets.session-hmac", "")
        );
        // Also accept un-namespaced security.yml (keys at root)
        if (!security.containsKey("security")) {
            securitySettings = new SecuritySettings(
                    YamlSupport.integer(security, "max-login-attempts", 5),
                    YamlSupport.integer(security, "lock-duration-seconds", 300),
                    YamlSupport.integer(security, "login-timeout-seconds", 60),
                    YamlSupport.integer(security, "minimum-password-length", 8),
                    YamlSupport.integer(security, "maximum-password-length", 64),
                    YamlSupport.bool(security, "reject-common-passwords", true),
                    YamlSupport.bool(security, "require-complexity", false),
                    YamlSupport.bool(security, "require-uppercase", false),
                    YamlSupport.bool(security, "require-special-character", false),
                    YamlSupport.bool(security, "rate-limit.enabled", true),
                    YamlSupport.integer(security, "rate-limit.attempts-per-minute", 10),
                    YamlSupport.string(security, "password-hashing.algorithm", "ARGON2ID"),
                    YamlSupport.integer(security, "password-hashing.memory-kib", 65536),
                    YamlSupport.integer(security, "password-hashing.iterations", 3),
                    YamlSupport.integer(security, "password-hashing.parallelism", 1),
                    YamlSupport.string(security, "secrets.ip-hmac", ""),
                    YamlSupport.string(security, "secrets.session-hmac", "")
            );
        }
        PremiumSettings premiumSettings = new PremiumSettings(
                YamlSupport.bool(premium, "premium.auto-login.enabled", YamlSupport.bool(premium, "auto-login.enabled", true)),
                YamlSupport.bool(premium, "premium.auto-login.success-message", YamlSupport.bool(premium, "auto-login.success-message", true)),
                YamlSupport.bool(premium, "premium.auto-login.require-verified-identity", YamlSupport.bool(premium, "auto-login.require-verified-identity", true)),
                YamlSupport.bool(premium, "premium.auto-login.bypass-password", YamlSupport.bool(premium, "auto-login.bypass-password", true)),
                YamlSupport.bool(premium, "premium.bridge.enabled", YamlSupport.bool(premium, "bridge.enabled", true)),
                YamlSupport.string(premium, "premium.bridge.channel", YamlSupport.string(premium, "bridge.channel", "blazemclogin:v1")),
                YamlSupport.integer(premium, "premium.bridge.max-message-age-seconds", YamlSupport.integer(premium, "bridge.max-message-age-seconds", 15)),
                YamlSupport.integer(premium, "premium.bridge.wait-for-bridge-millis", YamlSupport.integer(premium, "bridge.wait-for-bridge-millis", 1500)),
                YamlSupport.string(premium, "premium.bridge.shared-secret", YamlSupport.string(premium, "bridge.shared-secret", ""))
        );
        SessionSettings sessionSettings = new SessionSettings(
                YamlSupport.bool(sessions, "sessions.enabled", YamlSupport.bool(sessions, "enabled", true)),
                YamlSupport.bool(sessions, "sessions.remember-after-reconnect", YamlSupport.bool(sessions, "remember-after-reconnect", true)),
                YamlSupport.integer(sessions, "sessions.expiration-minutes", YamlSupport.integer(sessions, "expiration-minutes", 30)),
                YamlSupport.bool(sessions, "sessions.bind-to-ip", YamlSupport.bool(sessions, "bind-to-ip", false)),
                YamlSupport.bool(sessions, "sessions.invalidate-on-ip-change", YamlSupport.bool(sessions, "invalidate-on-ip-change", true)),
                YamlSupport.bool(sessions, "sessions.invalidate-on-password-change", YamlSupport.bool(sessions, "invalidate-on-password-change", true)),
                YamlSupport.bool(sessions, "sessions.prevent-multiple-sessions", YamlSupport.bool(sessions, "prevent-multiple-sessions", false))
        );
        AccountLimitSettings limitSettings = new AccountLimitSettings(
                YamlSupport.bool(limits, "account-limits.enabled", YamlSupport.bool(limits, "enabled", true)),
                YamlSupport.integer(limits, "account-limits.max-accounts-per-ip", YamlSupport.integer(limits, "max-accounts-per-ip", 3)),
                YamlSupport.bool(limits, "account-limits.count-premium-accounts", YamlSupport.bool(limits, "count-premium-accounts", true)),
                YamlSupport.bool(limits, "account-limits.count-cracked-accounts", YamlSupport.bool(limits, "count-cracked-accounts", true)),
                YamlSupport.stringList(limits, "account-limits.exempt-permissions",
                        YamlSupport.stringList(limits, "exempt-permissions", AccountLimitSettings.defaults().exemptPermissions())),
                YamlSupport.stringList(limits, "account-limits.whitelist", YamlSupport.stringList(limits, "whitelist", java.util.List.of())),
                YamlSupport.stringList(limits, "account-limits.blacklist", YamlSupport.stringList(limits, "blacklist", java.util.List.of()))
        );
        RestrictionSettings restrictionSettings = new RestrictionSettings(
                YamlSupport.bool(restrictions, "login-restrictions.enabled", YamlSupport.bool(restrictions, "enabled", true)),
                YamlSupport.bool(restrictions, "login-restrictions.freeze-player", YamlSupport.bool(restrictions, "freeze-player", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-inventory", YamlSupport.bool(restrictions, "disable-inventory", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-chat", YamlSupport.bool(restrictions, "disable-chat", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-damage", YamlSupport.bool(restrictions, "disable-damage", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-interactions", YamlSupport.bool(restrictions, "disable-interactions", true)),
                YamlSupport.bool(restrictions, "login-restrictions.teleport-to-login-spawn", YamlSupport.bool(restrictions, "teleport-to-login-spawn", false)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-move", YamlSupport.bool(restrictions, "disable-move", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-drop", YamlSupport.bool(restrictions, "disable-drop", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-pickup", YamlSupport.bool(restrictions, "disable-pickup", true)),
                YamlSupport.bool(restrictions, "login-restrictions.disable-commands", YamlSupport.bool(restrictions, "disable-commands", true)),
                YamlSupport.stringList(restrictions, "login-restrictions.allowed-commands",
                        YamlSupport.stringList(restrictions, "allowed-commands", RestrictionSettings.defaults().allowedCommands()))
        );
        DatabaseSettings databaseSettings = new DatabaseSettings(
                DatabaseSettings.parseType(YamlSupport.string(database, "database.type", YamlSupport.string(database, "type", "SQLITE"))),
                YamlSupport.string(database, "database.sqlite.file", YamlSupport.string(database, "sqlite.file", "accounts.db")),
                YamlSupport.string(database, "database.mysql.host", YamlSupport.string(database, "mysql.host", "localhost")),
                YamlSupport.integer(database, "database.mysql.port", YamlSupport.integer(database, "mysql.port", 3306)),
                YamlSupport.string(database, "database.mysql.database", YamlSupport.string(database, "mysql.database", "blazemclogin")),
                YamlSupport.string(database, "database.mysql.username", YamlSupport.string(database, "mysql.username", "root")),
                YamlSupport.string(database, "database.mysql.password", YamlSupport.string(database, "mysql.password", "CHANGE_ME")),
                YamlSupport.bool(database, "database.mysql.ssl", YamlSupport.bool(database, "mysql.ssl", true)),
                YamlSupport.integer(database, "database.mysql.pool-size", YamlSupport.integer(database, "mysql.pool-size", 10))
        );
        LoginSpawnSettings spawnSettings = new LoginSpawnSettings(
                YamlSupport.bool(spawn, "login-spawn.enabled", YamlSupport.bool(spawn, "enabled", false)),
                YamlSupport.string(spawn, "login-spawn.world", YamlSupport.string(spawn, "world", "world")),
                YamlSupport.decimal(spawn, "login-spawn.x", YamlSupport.decimal(spawn, "x", 0.5)),
                YamlSupport.decimal(spawn, "login-spawn.y", YamlSupport.decimal(spawn, "y", 80)),
                YamlSupport.decimal(spawn, "login-spawn.z", YamlSupport.decimal(spawn, "z", 0.5)),
                (float) YamlSupport.decimal(spawn, "login-spawn.yaw", YamlSupport.decimal(spawn, "yaw", 0)),
                (float) YamlSupport.decimal(spawn, "login-spawn.pitch", YamlSupport.decimal(spawn, "pitch", 0))
        );
        return new PluginSettings(generalSettings, securitySettings, premiumSettings, sessionSettings,
                limitSettings, restrictionSettings, databaseSettings, spawnSettings);
    }
}
