package net.blazemc.login.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SessionRepository {

    private final DatabaseManager database;

    public SessionRepository(DatabaseManager database) {
        this.database = database;
    }

    public void save(SessionRecord session) {
        String sql = """
                INSERT INTO sessions(session_id_hash, uuid, created_at, expires_at, ip_hash, password_version)
                VALUES(?,?,?,?,?,?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, session.sessionIdHash());
            statement.setString(2, session.uuid().toString());
            statement.setLong(3, session.createdAt());
            statement.setLong(4, session.expiresAt());
            statement.setString(5, session.ipHash());
            statement.setInt(6, session.passwordVersion());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("save session failed", ex);
        }
    }

    public Optional<SessionRecord> findLatest(UUID uuid, long now) {
        String sql = "SELECT * FROM sessions WHERE uuid = ? AND expires_at > ? ORDER BY created_at DESC LIMIT 1";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, now);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(read(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<SessionRecord> findAll(UUID uuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM sessions WHERE uuid = ? ORDER BY created_at DESC")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                List<SessionRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(read(rs));
                }
                return list;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void deleteByUuid(UUID uuid) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void deleteExpired(long now) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?")) {
            statement.setLong(1, now);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private SessionRecord read(ResultSet rs) throws SQLException {
        return new SessionRecord(
                rs.getString("session_id_hash"),
                UUID.fromString(rs.getString("uuid")),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getString("ip_hash"),
                rs.getInt("password_version")
        );
    }
}
