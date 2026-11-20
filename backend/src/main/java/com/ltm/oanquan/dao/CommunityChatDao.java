package com.ltm.oanquan.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Persistent community messages, independent of matches and rooms. */
public class CommunityChatDao {
    private volatile boolean initialized;

    private synchronized void initialize(Connection connection) throws SQLException {
        if (initialized) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS community_messages (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    username VARCHAR(255) NOT NULL,
                    content VARCHAR(500) NOT NULL,
                    sent_at_ms BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
        initialized = true;
    }

    public Map<String, Object> insert(long userId, String username, String content) {
        long now = System.currentTimeMillis();
        try (Connection connection = DatabaseConnection.layKetNoi()) {
            initialize(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO community_messages(user_id,username,content,sent_at_ms) VALUES(?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, userId);
                statement.setString(2, username);
                statement.setString(3, content);
                statement.setLong(4, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Missing message id");
                    return message(keys.getLong(1), userId, username, content, now);
                }
            }
        } catch (SQLException e) { throw new DaoException("Cannot save community message", e); }
    }

    public List<Map<String, Object>> history(long before, int limit) {
        try (Connection connection = DatabaseConnection.layKetNoi()) {
            initialize(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM community_messages WHERE id < ? ORDER BY id DESC LIMIT ?")) {
                statement.setLong(1, before);
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    while (rows.next()) result.add(message(rows.getLong("id"), rows.getLong("user_id"),
                            rows.getString("username"), rows.getString("content"), rows.getLong("sent_at_ms")));
                    return result;
                }
            }
        } catch (SQLException e) { throw new DaoException("Cannot load community history", e); }
    }

    private Map<String, Object> message(long id, long userId, String username, String content, long time) {
        return Map.of("id", Long.toString(id), "userId", userId, "username", username,
                "content", content, "sentAt", Instant.ofEpochMilli(time).toString());
    }
}
