package net.blazemc.login.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.blazemc.login.model.AccountStatus;
import net.blazemc.login.model.AccountType;

public final class AccountRepository {

    private final DatabaseManager database;

    public AccountRepository(DatabaseManager database) {
        this.database = database;
    }

    public Optional<AccountRecord> findByUuid(UUID uuid) {
        return queryOne("SELECT * FROM accounts WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()));
    }

    public Optional<AccountRecord> findByUsername(String username) {
        return queryOne("SELECT * FROM accounts WHERE username_lower = ?",
                ps -> ps.setString(1, username.toLowerCase(Locale.ROOT)));
    }

    public List<AccountRecord> findByIpHash(String ipHash) {
        return queryMany("SELECT * FROM accounts WHERE ip_hash = ?", ps -> ps.setString(1, ipHash));
    }

    public int countByIpHash(String ipHash, boolean countPremium, boolean countCracked) {
        if (!countPremium && !countCracked) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM accounts WHERE ip_hash = ?");
        if (countPremium && !countCracked) {
            sql.append(" AND account_type = 'PREMIUM'");
        } else if (!countPremium) {
            sql.append(" AND account_type = 'CRACKED'");
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, ipHash);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("countByIpHash failed", ex);
        }
    }

    public void save(AccountRecord account) {
        String sql = database.dialect().upsertAccounts();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAccount(statement, account);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("save account failed", ex);
        }
    }

    public boolean delete(UUID uuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("delete account failed", ex);
        }
    }

    public int countAll() {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM accounts");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<AccountRecord> all() {
        return queryMany("SELECT * FROM accounts", ps -> {
        });
    }

    private void bindAccount(PreparedStatement statement, AccountRecord account) throws SQLException {
        statement.setString(1, account.uuid().toString());
        statement.setString(2, account.username());
        statement.setString(3, account.usernameLower());
        statement.setString(4, account.accountType().name());
        statement.setString(5, account.passwordHash());
        statement.setString(6, account.hashAlgorithm());
        statement.setLong(7, account.registeredAt());
        statement.setLong(8, account.lastLoginAt());
        statement.setString(9, account.ipHash());
        statement.setString(10, account.status().name());
        statement.setInt(11, account.passwordVersion());
        statement.setString(12, account.premiumUuid() == null ? null : account.premiumUuid().toString());
    }

    private Optional<AccountRecord> queryOne(String sql, SqlBinder binder) {
        List<AccountRecord> all = queryMany(sql, binder);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    private List<AccountRecord> queryMany(String sql, SqlBinder binder) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                List<AccountRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(read(rs));
                }
                return records;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("query failed: " + sql, ex);
        }
    }

    private AccountRecord read(ResultSet rs) throws SQLException {
        String premium = rs.getString("premium_uuid");
        return new AccountRecord(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("username_lower"),
                AccountType.fromString(rs.getString("account_type")),
                rs.getString("password_hash"),
                rs.getString("hash_algorithm"),
                rs.getLong("registered_at"),
                rs.getLong("last_login_at"),
                rs.getString("ip_hash"),
                AccountStatus.fromString(rs.getString("status")),
                rs.getInt("password_version"),
                premium == null || premium.isBlank() ? null : UUID.fromString(premium)
        );
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
