package net.blazemc.login.storage;

import net.blazemc.login.config.DatabaseSettings;

public final class SqlDialect {

    private final DatabaseSettings.Type type;

    public SqlDialect(DatabaseSettings.Type type) {
        this.type = type;
    }

    public boolean sqlite() {
        return type == DatabaseSettings.Type.SQLITE;
    }

    public String autoIncrement() {
        return sqlite() ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
    }

    public String text() {
        return sqlite() ? "TEXT" : "VARCHAR(255)";
    }

    public String longText() {
        return sqlite() ? "TEXT" : "TEXT";
    }

    public String upsertAccounts() {
        if (sqlite()) {
            return """
                    INSERT INTO accounts(uuid, username, username_lower, account_type, password_hash, hash_algorithm,
                      registered_at, last_login_at, ip_hash, status, password_version, premium_uuid)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET
                      username=excluded.username,
                      username_lower=excluded.username_lower,
                      account_type=excluded.account_type,
                      password_hash=excluded.password_hash,
                      hash_algorithm=excluded.hash_algorithm,
                      last_login_at=excluded.last_login_at,
                      ip_hash=excluded.ip_hash,
                      status=excluded.status,
                      password_version=excluded.password_version,
                      premium_uuid=excluded.premium_uuid
                    """;
        }
        return """
                INSERT INTO accounts(uuid, username, username_lower, account_type, password_hash, hash_algorithm,
                  registered_at, last_login_at, ip_hash, status, password_version, premium_uuid)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  username=VALUES(username),
                  username_lower=VALUES(username_lower),
                  account_type=VALUES(account_type),
                  password_hash=VALUES(password_hash),
                  hash_algorithm=VALUES(hash_algorithm),
                  last_login_at=VALUES(last_login_at),
                  ip_hash=VALUES(ip_hash),
                  status=VALUES(status),
                  password_version=VALUES(password_version),
                  premium_uuid=VALUES(premium_uuid)
                """;
    }
}
