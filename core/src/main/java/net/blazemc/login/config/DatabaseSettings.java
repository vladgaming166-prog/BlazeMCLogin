package net.blazemc.login.config;

import java.util.Locale;

public record DatabaseSettings(
        Type type,
        String sqliteFile,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        boolean mysqlSsl,
        int poolSize
) {
    public enum Type {
        SQLITE, MYSQL, MARIADB
    }

    public static DatabaseSettings defaults() {
        return new DatabaseSettings(
                Type.SQLITE, "accounts.db",
                "localhost", 3306, "blazemclogin", "root", "CHANGE_ME", true, 10
        );
    }

    public static Type parseType(String raw) {
        if (raw == null) {
            return Type.SQLITE;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.equals("MARIA") || value.equals("MARIADB")) {
            return Type.MARIADB;
        }
        if (value.equals("MYSQL")) {
            return Type.MYSQL;
        }
        return Type.SQLITE;
    }

    public boolean isMysqlFamily() {
        return type == Type.MYSQL || type == Type.MARIADB;
    }
}
