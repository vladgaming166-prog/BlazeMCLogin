package net.blazemc.login.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.blazemc.login.config.DatabaseSettings;

public final class BackupService {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final DatabaseManager database;

    public BackupService(DatabaseManager database) {
        this.database = database;
    }

    public Path backup() throws IOException {
        Path dir = database.dataFolder().resolve("backups");
        Files.createDirectories(dir);
        String stamp = FORMAT.format(Instant.now());
        if (database.settings().type() == DatabaseSettings.Type.SQLITE) {
            Path source = database.dataFolder().resolve("database").resolve(database.settings().sqliteFile());
            Path dest = dir.resolve("accounts-" + stamp + ".db");
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }
        Path dest = dir.resolve("accounts-" + stamp + ".sql");
        Files.writeString(dest, dumpSql());
        return dest;
    }

    public String dumpSql() {
        StringBuilder out = new StringBuilder();
        out.append("-- BlazeMCLogin backup ").append(Instant.now()).append('\n');
        dumpTable(out, "accounts");
        dumpTable(out, "sessions");
        dumpTable(out, "ip_lists");
        return out.toString();
    }

    public List<Map<String, Object>> exportAccounts() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM accounts");
             ResultSet rs = statement.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String column = meta.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    if ("password_hash".equalsIgnoreCase(column)) {
                        row.put(column, value == null ? null : "<hash>");
                    } else {
                        row.put(column, value);
                    }
                }
                rows.add(row);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        return rows;
    }

    private void dumpTable(StringBuilder out, String table) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table);
             ResultSet rs = statement.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                out.append("INSERT INTO ").append(table).append(" VALUES(");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    if (i > 1) {
                        out.append(',');
                    }
                    Object value = rs.getObject(i);
                    if (value == null) {
                        out.append("NULL");
                    } else if (value instanceof Number) {
                        out.append(value);
                    } else {
                        out.append('\'').append(value.toString().replace("'", "''")).append('\'');
                    }
                }
                out.append(");\n");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
