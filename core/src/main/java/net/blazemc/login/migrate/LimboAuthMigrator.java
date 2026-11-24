package net.blazemc.login.migrate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.blazemc.login.model.AccountStatus;
import net.blazemc.login.model.AccountType;
import net.blazemc.login.storage.AccountRecord;
import net.blazemc.login.storage.AccountRepository;
import net.blazemc.login.storage.BackupService;

public final class LimboAuthMigrator {

    public record Report(
            boolean sourceFound,
            Path source,
            Path backup,
            int inspected,
            int imported,
            int skippedDuplicate,
            int skippedIncompatibleHash,
            List<String> notes
    ) {
        public String asText() {
            StringBuilder out = new StringBuilder();
            out.append("BlazeMCLogin LimboAuth migration report\n");
            out.append("source: ").append(sourceFound ? source : "not found").append('\n');
            out.append("backup: ").append(backup).append('\n');
            out.append("inspected: ").append(inspected).append('\n');
            out.append("imported: ").append(imported).append('\n');
            out.append("skipped duplicates: ").append(skippedDuplicate).append('\n');
            out.append("skipped incompatible hashes: ").append(skippedIncompatibleHash).append('\n');
            for (String note : notes) {
                out.append("- ").append(note).append('\n');
            }
            return out.toString();
        }
    }

    private final AccountRepository accounts;
    private final BackupService backups;

    public LimboAuthMigrator(AccountRepository accounts, BackupService backups) {
        this.accounts = accounts;
        this.backups = backups;
    }

    public Report migrate(Path candidate, boolean overwrite) throws Exception {
        List<String> notes = new ArrayList<>();
        Path source = resolveSource(candidate);
        if (source == null || !Files.exists(source)) {
            notes.add("No LimboAuth database was found. Looked for limboauth.sqlite, auth.db, and limboauth.db.");
            return new Report(false, candidate, null, 0, 0, 0, 0, notes);
        }
        Path backup = backups.backup();
        notes.add("Created BlazeMCLogin backup before import. The original LimboAuth database was not modified.");
        Class.forName("org.sqlite.JDBC");
        int inspected = 0;
        int imported = 0;
        int skippedDuplicate = 0;
        int skippedHash = 0;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source.toAbsolutePath())) {
            Optional<String> table = detectAuthTable(connection);
            if (table.isEmpty()) {
                notes.add("The LimboAuth file exists but no AUTH / AUTHENTICATION table was found. Nothing imported.");
                return new Report(true, source, backup, 0, 0, 0, 0, notes);
            }
            notes.add("Using table " + table.get());
            Set<String> columns = columns(connection, table.get());
            notes.add("Columns: " + String.join(", ", columns));
            String nickCol = first(columns, "NICKNAME", "NICK", "USERNAME", "LOWERCASENICKNAME");
            String hashCol = first(columns, "HASH", "PASSWORD", "PASSWORDHASH");
            String uuidCol = first(columns, "UUID", "PREMIUMUUID", "UNIQUEID");
            String premiumCol = first(columns, "PREMIUMUUID");
            String ipCol = first(columns, "IP", "REGIP", "LOGINIP");
            String dateCol = first(columns, "REGDATE", "REG_DATE", "REGISTERED");
            if (nickCol == null) {
                notes.add("Cannot import: no username column.");
                return new Report(true, source, backup, 0, 0, 0, 0, notes);
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + table.get())) {
                while (rs.next()) {
                    inspected++;
                    String username = rs.getString(nickCol);
                    if (username == null || username.isBlank()) {
                        continue;
                    }
                    String hash = hashCol == null ? null : rs.getString(hashCol);
                    if (!isImportableHash(hash)) {
                        skippedHash++;
                        notes.add("Skipped " + username + ": hash format is not Argon2/BCrypt/SHA256 PHC. Player must /register again after a staff reset.");
                        continue;
                    }
                    UUID uuid = readUuid(rs, uuidCol, username);
                    Optional<AccountRecord> existing = accounts.findByUuid(uuid).or(() -> accounts.findByUsername(username));
                    if (existing.isPresent() && !overwrite) {
                        skippedDuplicate++;
                        continue;
                    }
                    UUID premium = readUuid(rs, premiumCol, null);
                    AccountType type = premium != null ? AccountType.PREMIUM : AccountType.CRACKED;
                    long registered = dateCol == null ? System.currentTimeMillis() : coerceMillis(rs.getObject(dateCol));
                    String algorithm = detectAlgorithm(hash);
                    AccountRecord record = new AccountRecord(
                            uuid,
                            username,
                            username.toLowerCase(Locale.ROOT),
                            type,
                            hash,
                            algorithm,
                            registered,
                            registered,
                            null,
                            AccountStatus.ACTIVE,
                            1,
                            premium
                    );
                    accounts.save(record);
                    imported++;
                    if (ipCol != null && rs.getString(ipCol) != null) {
                        notes.add("IP for " + username + " was not copied as plaintext. Re-association happens on next login.");
                    }
                }
            }
        }
        notes.add("If a hash could not be imported, reset the account with /bmlogin resetpassword <player> and ask the player to /register. Never reuse plaintext recovery.");
        return new Report(true, source, backup, inspected, imported, skippedDuplicate, skippedHash, notes);
    }

    private Path resolveSource(Path candidate) {
        if (candidate != null && Files.exists(candidate)) {
            return candidate;
        }
        Path[] guesses = new Path[] {
                Path.of("plugins/limboauth/limboauth.sqlite"),
                Path.of("plugins/limboauth/auth.db"),
                Path.of("plugins/LimboAuth/limboauth.sqlite"),
                Path.of("limboauth.sqlite"),
                Path.of("auth.db")
        };
        for (Path guess : guesses) {
            if (Files.exists(guess)) {
                return guess;
            }
        }
        return candidate;
    }

    private Optional<String> detectAuthTable(Connection connection) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && (name.equalsIgnoreCase("AUTH") || name.equalsIgnoreCase("AUTHENTICATION")
                        || name.equalsIgnoreCase("AUTHUSER") || name.toLowerCase(Locale.ROOT).contains("auth"))) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
    }

    private Set<String> columns(Connection connection, String table) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + table + " LIMIT 0")) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                names.add(meta.getColumnLabel(i).toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private String first(Set<String> columns, String... names) {
        for (String name : names) {
            if (columns.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private boolean isImportableHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        String value = hash.trim();
        return value.startsWith("$argon2") || value.startsWith("$2a$") || value.startsWith("$2b$")
                || value.startsWith("$2y$") || value.startsWith("$SHA256$") || value.startsWith("SHA256$")
                || (value.length() == 64 && value.matches("[0-9a-fA-F]+"));
    }

    private String detectAlgorithm(String hash) {
        if (hash.startsWith("$argon2id$")) {
            return "ARGON2ID";
        }
        if (hash.startsWith("$argon2")) {
            return "ARGON2";
        }
        if (hash.startsWith("$2")) {
            return "BCRYPT";
        }
        return "SHA256";
    }

    private UUID readUuid(ResultSet rs, String column, String username) {
        if (column == null) {
            return username == null ? UUID.randomUUID() : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        }
        try {
            String value = rs.getString(column);
            if (value == null || value.isBlank()) {
                return username == null ? null : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
            }
            String normalized = value.contains("-") ? value
                    : value.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5");
            return UUID.fromString(normalized);
        } catch (Exception ex) {
            return username == null ? null : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        }
    }

    private long coerceMillis(Object value) {
        if (value instanceof Number number) {
            long v = number.longValue();
            if (v < 10_000_000_000L) {
                return v * 1000L;
            }
            return v;
        }
        try {
            return Instant.parse(String.valueOf(value)).toEpochMilli();
        } catch (Exception ex) {
            return System.currentTimeMillis();
        }
    }
}
