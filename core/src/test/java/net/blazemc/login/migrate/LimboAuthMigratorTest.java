package net.blazemc.login.migrate;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.Logger;
import net.blazemc.login.config.DatabaseSettings;
import net.blazemc.login.storage.AccountRepository;
import net.blazemc.login.storage.BackupService;
import net.blazemc.login.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class LimboAuthMigratorTest {

    @TempDir
    Path temp;

    @Test
    void inspectsSchemaImportsArgon2AndSkipsUnknownHashesWithoutTouchingSource() throws Exception {
        Path source = temp.resolve("limboauth.sqlite");
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE AUTH (NICKNAME TEXT, HASH TEXT, UUID TEXT, REGDATE INTEGER)");
            statement.executeUpdate("INSERT INTO AUTH VALUES('GoodUser','$argon2id$v=19$m=8192,t=1,p=1$YWFhYWFhYWE$YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE','11111111111111111111111111111111',1)");
            statement.executeUpdate("INSERT INTO AUTH VALUES('BadUser','plaintext-not-a-hash','22222222222222222222222222222222',1)");
        }
        long originalSize = java.nio.file.Files.size(source);
        DatabaseManager database = new DatabaseManager(
                new DatabaseSettings(DatabaseSettings.Type.SQLITE, "accounts.db", "h", 3306, "d", "u", "p", false, 1),
                temp.resolve("dest"),
                Logger.getLogger("mig")
        );
        java.nio.file.Files.createDirectories(temp.resolve("dest"));
        database.open();
        AccountRepository accounts = new AccountRepository(database);
        LimboAuthMigrator.Report report = new LimboAuthMigrator(accounts, new BackupService(database)).migrate(source, false);
        assertThat(report.sourceFound()).isTrue();
        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.skippedIncompatibleHash()).isEqualTo(1);
        assertThat(report.asText()).contains("hash format");
        assertThat(java.nio.file.Files.size(source)).isEqualTo(originalSize);
        assertThat(accounts.findByUsername("GoodUser")).isPresent();
        assertThat(accounts.findByUsername("BadUser")).isEmpty();
        database.close();
    }
}
