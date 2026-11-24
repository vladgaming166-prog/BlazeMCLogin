package net.blazemc.login.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SchemaMigrator {

    private final DatabaseManager database;
    private final SqlDialect dialect;
    private final Logger logger;

    public SchemaMigrator(DatabaseManager database, SqlDialect dialect, Logger logger) {
        this.database = database;
        this.dialect = dialect;
        this.logger = logger;
    }

    public void migrate() {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                      version INTEGER PRIMARY KEY,
                      name VARCHAR(128) NOT NULL,
                      applied_at BIGINT NOT NULL
                    )
                    """);
            int current = currentVersion(connection);
            List<Migration> migrations = migrations();
            for (Migration migration : migrations) {
                if (migration.version() <= current) {
                    continue;
                }
                connection.setAutoCommit(false);
                try {
                    for (String sql : migration.sql()) {
                        statement.executeUpdate(sql);
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO schema_migrations(version, name, applied_at) VALUES (?,?,?)")) {
                        insert.setInt(1, migration.version());
                        insert.setString(2, migration.name());
                        insert.setLong(3, System.currentTimeMillis());
                        insert.executeUpdate();
                    }
                    connection.commit();
                    logger.info("Applied database migration " + migration.version() + ": " + migration.name());
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Database migration failed", ex);
            throw new IllegalStateException("database migration failed", ex);
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(version) FROM schema_migrations");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }

    private List<Migration> migrations() {
        List<Migration> list = new ArrayList<>();
        String idType = dialect.sqlite() ? "TEXT" : "CHAR(36)";
        list.add(new Migration(1, "initial_schema", List.of(
                """
                CREATE TABLE IF NOT EXISTS accounts (
                  uuid %s PRIMARY KEY,
                  username VARCHAR(16) NOT NULL,
                  username_lower VARCHAR(16) NOT NULL UNIQUE,
                  account_type VARCHAR(16) NOT NULL,
                  password_hash TEXT,
                  hash_algorithm VARCHAR(32),
                  registered_at BIGINT NOT NULL,
                  last_login_at BIGINT NOT NULL,
                  ip_hash CHAR(64),
                  status VARCHAR(32) NOT NULL,
                  password_version INTEGER NOT NULL,
                  premium_uuid %s
                )
                """.formatted(idType, idType),
                """
                CREATE TABLE IF NOT EXISTS sessions (
                  session_id_hash CHAR(64) PRIMARY KEY,
                  uuid %s NOT NULL,
                  created_at BIGINT NOT NULL,
                  expires_at BIGINT NOT NULL,
                  ip_hash CHAR(64),
                  password_version INTEGER NOT NULL
                )
                """.formatted(idType),
                "CREATE INDEX IF NOT EXISTS idx_sessions_uuid ON sessions(uuid)",
                "CREATE INDEX IF NOT EXISTS idx_accounts_ip ON accounts(ip_hash)",
                """
                CREATE TABLE IF NOT EXISTS used_nonces (
                  nonce CHAR(64) PRIMARY KEY,
                  expires_at BIGINT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ip_lists (
                  ip_hash CHAR(64) NOT NULL,
                  list_type VARCHAR(16) NOT NULL,
                  created_at BIGINT NOT NULL,
                  PRIMARY KEY (ip_hash, list_type)
                )
                """
        )));
        return list;
    }

    private record Migration(int version, String name, List<String> sql) {
    }
}
