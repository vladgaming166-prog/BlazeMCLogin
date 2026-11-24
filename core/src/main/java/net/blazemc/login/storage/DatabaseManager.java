package net.blazemc.login.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;
import net.blazemc.login.config.DatabaseSettings;

public final class DatabaseManager implements AutoCloseable {

    private final DatabaseSettings settings;
    private final Path dataFolder;
    private final Logger logger;
    private final SqlDialect dialect;
    private volatile HikariDataSource dataSource;

    public DatabaseManager(DatabaseSettings settings, Path dataFolder, Logger logger) {
        this.settings = Objects.requireNonNull(settings);
        this.dataFolder = Objects.requireNonNull(dataFolder);
        this.logger = logger;
        this.dialect = new SqlDialect(settings.type());
    }

    public void open() {
        closeQuietly();
        HikariConfig config = new HikariConfig();
        config.setPoolName("BlazeMCLogin");
        config.setMaximumPoolSize(Math.max(2, settings.poolSize()));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(1_800_000);
        config.setInitializationFailTimeout(15_000);
        if (settings.type() == DatabaseSettings.Type.SQLITE) {
            Path file = dataFolder.resolve("database").resolve(settings.sqliteFile());
            file.getParent().toFile().mkdirs();
            config.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setIdleTimeout(0);
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.setConnectionInitSql("PRAGMA foreign_keys = ON");
        } else {
            String host = settings.mysqlHost();
            int port = settings.mysqlPort();
            String db = settings.mysqlDatabase();
            String params = "useUnicode=true&characterEncoding=utf8&cachePrepStmts=true&prepStmtCacheSize=250"
                    + "&prepStmtCacheSqlLimit=2048&useSSL=" + settings.mysqlSsl()
                    + "&requireSSL=" + settings.mysqlSsl()
                    + "&allowPublicKeyRetrieval=" + !settings.mysqlSsl();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params);
            config.setUsername(settings.mysqlUsername());
            config.setPassword(settings.mysqlPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        this.dataSource = new HikariDataSource(config);
        new SchemaMigrator(this, dialect, logger).migrate();
    }

    public Connection connection() throws SQLException {
        HikariDataSource ds = dataSource;
        if (ds == null || ds.isClosed()) {
            throw new SQLException("database is not open");
        }
        return ds.getConnection();
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public DatabaseSettings settings() {
        return settings;
    }

    public Path dataFolder() {
        return dataFolder;
    }

    public boolean isOpen() {
        HikariDataSource ds = dataSource;
        return ds != null && !ds.isClosed();
    }

    public void reconnect() {
        logger.info("[BlazeMCLogin] Reconnecting database...");
        open();
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        HikariDataSource ds = dataSource;
        dataSource = null;
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }
}
