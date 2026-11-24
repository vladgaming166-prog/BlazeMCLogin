package net.blazemc.login.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.blazemc.login.model.LanguageCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @TempDir
    Path temp;

    @Test
    void loadsSplitYamlFilesAndReloadChangesLanguage() throws Exception {
        Files.writeString(temp.resolve("config.yml"), "language: en\ndebug: true\n", StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("security.yml"), """
                max-login-attempts: 7
                lock-duration-seconds: 10
                login-timeout-seconds: 20
                minimum-password-length: 10
                maximum-password-length: 32
                rate-limit:
                  enabled: false
                  attempts-per-minute: 3
                password-hashing:
                  algorithm: ARGON2ID
                  memory-kib: 8192
                  iterations: 2
                  parallelism: 1
                """, StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("premium.yml"), """
                premium:
                  auto-login:
                    enabled: false
                    success-message: false
                    require-verified-identity: true
                    bypass-password: false
                  bridge:
                    enabled: true
                    shared-secret: aabbcc
                """, StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("sessions.yml"), "sessions:\n  enabled: false\n  expiration-minutes: 5\n", StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("account-limits.yml"), "account-limits:\n  enabled: true\n  max-accounts-per-ip: 0\n", StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("login-restrictions.yml"), "login-restrictions:\n  allowed-commands:\n    - /login\n", StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("database.yml"), "database:\n  type: SQLITE\n  sqlite:\n    file: custom.db\n", StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("login-spawn.yml"), "login-spawn:\n  enabled: true\n  world: spawn\n  y: 64\n", StandardCharsets.UTF_8);

        PluginSettings settings = new ConfigLoader().load(temp);
        assertThat(settings.language()).isEqualTo(LanguageCode.EN);
        assertThat(settings.security().maxLoginAttempts()).isEqualTo(7);
        assertThat(settings.security().minimumPasswordLength()).isEqualTo(10);
        assertThat(settings.premium().autoLoginEnabled()).isFalse();
        assertThat(settings.sessions().enabled()).isFalse();
        assertThat(settings.accountLimits().unlimited()).isTrue();
        assertThat(settings.database().sqliteFile()).isEqualTo("custom.db");
        assertThat(settings.loginSpawn().world()).isEqualTo("spawn");

        Files.writeString(temp.resolve("config.yml"), "language: ro-ascii\n", StandardCharsets.UTF_8);
        PluginSettings reloaded = new ConfigLoader().load(temp);
        assertThat(reloaded.language()).isEqualTo(LanguageCode.RO_ASCII);
    }
}
