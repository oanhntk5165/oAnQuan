package com.ltm.oanquan.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MatchDao:
 * - Ghi kết quả trận, nước đi, lịch sử chat, thông tin room.
 * - Dùng JDBC trực tiếp với MySQL.
 * - Là lớp lưu trữ dữ liệu nghiệp vụ của game.
 */
public class MatchDao {

    public record Completion(int score0, int score1, int time0Ms, int time1Ms,
                             String result, String reason, int ratingDelta0,
                             int ratingDelta1, int pointsDelta0, int pointsDelta1) { }

    public long taoVan(Long player0, Long player1, String mode, int timeControlMs) {
        String sql = "INSERT INTO matches "
                + "(player1_id, player2_id, mode, time_control_ms) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.layKetNoi();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (player0 == null) statement.setNull(1, java.sql.Types.BIGINT);
            else statement.setLong(1, player0);
            if (player1 == null) statement.setNull(2, java.sql.Types.BIGINT);
            else statement.setLong(2, player1);
            statement.setString(3, mode);
            statement.setInt(4, timeControlMs);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Khong lay duoc id van dau");
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi tao van dau", e);
        }
    }

    public void ghiNuoc(long matchId, int moveNo, int playerSlot, String action,
                        Integer startPit, Integer direction, int gained, int spentMs,
                        String boardJson, Completion completion) {
        String sql = "INSERT INTO match_moves "
                + "(match_id, move_no, player_slot, action, start_pit, direction, gained, "
                + "time_spent_ms, board_after) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.layKetNoi()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, matchId);
                    statement.setInt(2, moveNo);
                    statement.setInt(3, playerSlot);
                    statement.setString(4, action);
                    if (startPit == null) statement.setNull(5, java.sql.Types.TINYINT);
                    else statement.setInt(5, startPit);
                    if (direction == null) statement.setNull(6, java.sql.Types.TINYINT);
                    else statement.setInt(6, direction);
                    statement.setInt(7, gained);
                    statement.setInt(8, spentMs);
                    statement.setString(9, boardJson);
                    statement.executeUpdate();
                }
                if (completion != null) ketThucTrongGiaoDich(connection, matchId, completion);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi luu nuoc di", e);
        }
    }

    public void ketThucVan(long matchId, Completion completion) {
        try (Connection connection = DatabaseConnection.layKetNoi()) {
            connection.setAutoCommit(false);
            try {
                ketThucTrongGiaoDich(connection, matchId, completion);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi luu ket qua van dau", e);
        }
    }

    private void ketThucTrongGiaoDich(Connection connection, long matchId,
                                      Completion completion) throws SQLException {
        String sql = "UPDATE matches SET score1=?, score2=?, time_used1_ms=?, time_used2_ms=?, "
                + "result=?, end_reason=?, rating_change1=?, rating_change2=?, "
                + "points_change1=?, points_change2=?, ended_at=CURRENT_TIMESTAMP "
                + "WHERE id=? AND mode='RANKED' AND ended_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, completion.score0());
            statement.setInt(2, completion.score1());
            statement.setInt(3, completion.time0Ms());
            statement.setInt(4, completion.time1Ms());
            statement.setString(5, completion.result());
            statement.setString(6, completion.reason());
            statement.setInt(7, completion.ratingDelta0());
            statement.setInt(8, completion.ratingDelta1());
            statement.setInt(9, completion.pointsDelta0());
            statement.setInt(10, completion.pointsDelta1());
            statement.setLong(11, matchId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Van dau khong ton tai hoac da ket thuc");
            }
        }
        capNhatNguoiChoi(connection, matchId, 0,
                completion.pointsDelta0(), completion.ratingDelta0());
        capNhatNguoiChoi(connection, matchId, 1,
                completion.pointsDelta1(), completion.ratingDelta1());
    }

    private void capNhatNguoiChoi(Connection connection, long matchId, int slot,
                                  int points, int rating) throws SQLException {
        String sql = "UPDATE users SET points = GREATEST(0, points + ?), rating = rating + ? "
                + "WHERE id = (SELECT player" + (slot + 1) + "_id FROM matches WHERE id = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, points);
            statement.setInt(2, rating);
            statement.setLong(3, matchId);
            statement.executeUpdate();
        }
    }

    public List<Map<String, Object>> lichSu(long userId, int gioiHan) {
        int limit = Math.max(1, Math.min(gioiHan, 100));
        String sql = "SELECT m.id, m.mode, m.score1, m.score2, m.result, m.end_reason, "
                + "m.rating_change1, m.rating_change2, m.points_change1, m.points_change2, "
                + "m.started_at, m.ended_at, "
                + "CASE WHEN m.player1_id = ? THEN 0 ELSE 1 END AS player_slot, "
                + "CASE WHEN m.player1_id = ? THEN COALESCE(u2.username, 'Máy') "
                + "ELSE u1.username END AS opponent "
                + "FROM matches m LEFT JOIN users u1 ON u1.id=m.player1_id "
                + "LEFT JOIN users u2 ON u2.id=m.player2_id "
                + "WHERE (m.player1_id = ? OR m.player2_id = ?) AND m.ended_at IS NOT NULL "
                + "AND m.mode = 'RANKED' "
                + "ORDER BY m.ended_at DESC LIMIT ?";
        try (Connection connection = DatabaseConnection.layKetNoi();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            statement.setLong(3, userId);
            statement.setLong(4, userId);
            statement.setInt(5, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> matches = new ArrayList<>();
                while (rows.next()) {
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("matchId", rows.getLong("id"));
                    match.put("mode", rows.getString("mode"));
                    match.put("opponent", rows.getString("opponent"));
                    match.put("score1", rows.getObject("score1"));
                    match.put("score2", rows.getObject("score2"));
                    String result = rows.getString("result");
                    int slot = rows.getInt("player_slot");
                    match.put("result", result);
                    match.put("playerSlot", slot);
                    match.put("myResult", ketQuaNguoiChoi(result, slot));
                    match.put("endReason", rows.getString("end_reason"));
                    match.put("ratingChange1", rows.getInt("rating_change1"));
                    match.put("ratingChange2", rows.getInt("rating_change2"));
                    match.put("pointsChange1", rows.getInt("points_change1"));
                    match.put("pointsChange2", rows.getInt("points_change2"));
                    match.put("startedAt", rows.getTimestamp("started_at"));
                    match.put("endedAt", rows.getTimestamp("ended_at"));
                    matches.add(match);
                }
                return matches;
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi truy van lich su dau", e);
        }
    }

    /** Lifetime totals grouped by game mode; this scans every completed match, not a recent-history page. */
    public List<Map<String, Object>> thongKe(long userId) {
        String sql = "SELECT mode, COUNT(*) AS games, "
                + "SUM(CASE WHEN result='DRAW' THEN 1 ELSE 0 END) AS draws, "
                + "SUM(CASE WHEN (player1_id=? AND result='P1_WIN') "
                + "OR (player2_id=? AND result='P2_WIN') THEN 1 ELSE 0 END) AS wins "
                + "FROM matches WHERE (player1_id=? OR player2_id=?) "
                + "AND mode='RANKED' AND ended_at IS NOT NULL GROUP BY mode";
        try (Connection connection = DatabaseConnection.layKetNoi();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            statement.setLong(3, userId);
            statement.setLong(4, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> stats = new ArrayList<>();
                while (rows.next()) {
                    int games = rows.getInt("games");
                    int wins = rows.getInt("wins");
                    int draws = rows.getInt("draws");
                    stats.add(Map.of("mode", rows.getString("mode"), "games", games,
                            "wins", wins, "draws", draws, "losses", games - wins - draws));
                }
                return stats;
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi truy van thong ke van dau", e);
        }
    }

    private String ketQuaNguoiChoi(String result, int playerSlot) {
        if (result == null || "DRAW".equals(result)) return result;
        boolean won = playerSlot == 0 ? "P1_WIN".equals(result) : "P2_WIN".equals(result);
        return won ? "WIN" : "LOSS";
    }

    public Map<String, Object> chiTiet(long matchId, long userId) {
        String sql = "SELECT m.id, m.player1_id, m.player2_id, m.mode, m.time_control_ms, "
                + "m.score1, m.score2, m.time_used1_ms, m.time_used2_ms, m.result, m.end_reason, "
                + "m.rating_change1, m.rating_change2, m.points_change1, m.points_change2, "
                + "m.started_at, m.ended_at, "
                + "u1.username AS player1, u2.username AS player2 "
                + "FROM matches m JOIN users u1 ON u1.id=m.player1_id "
                + "LEFT JOIN users u2 ON u2.id=m.player2_id WHERE m.id=? "
                + "AND m.mode='RANKED' AND (m.player1_id=? OR m.player2_id=?)";
        try (Connection connection = DatabaseConnection.layKetNoi()) {
            Map<String, Object> match = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, matchId);
                statement.setLong(2, userId);
                statement.setLong(3, userId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) return null;
                    match.put("matchId", row.getLong("id"));
                    match.put("mode", row.getString("mode"));
                    match.put("player1", row.getString("player1"));
                    match.put("player2", row.getString("player2") == null ? "Máy" : row.getString("player2"));
                    match.put("timeControlMs", row.getInt("time_control_ms"));
                    match.put("score1", row.getObject("score1"));
                    match.put("score2", row.getObject("score2"));
                    match.put("timeUsed1Ms", row.getObject("time_used1_ms"));
                    match.put("timeUsed2Ms", row.getObject("time_used2_ms"));
                    String result = row.getString("result");
                    int slot = row.getLong("player1_id") == userId ? 0 : 1;
                    match.put("result", result);
                    match.put("myResult", ketQuaNguoiChoi(result, slot));
                    match.put("endReason", row.getString("end_reason"));
                    match.put("ratingChange1", row.getInt("rating_change1"));
                    match.put("ratingChange2", row.getInt("rating_change2"));
                    match.put("pointsChange1", row.getInt("points_change1"));
                    match.put("pointsChange2", row.getInt("points_change2"));
                    match.put("startedAt", row.getTimestamp("started_at"));
                    match.put("endedAt", row.getTimestamp("ended_at"));
                    match.put("playerSlot", slot);
                }
            }
            return Map.of("match", match, "moves", nuocDi(connection, matchId),
                    "chat", tinNhan(connection, matchId));
        } catch (SQLException e) {
            throw new DaoException("Loi khi truy van chi tiet van dau", e);
        }
    }

    private List<Map<String, Object>> nuocDi(Connection connection, long matchId) throws SQLException {
        String sql = "SELECT move_no, player_slot, action, start_pit, direction, gained, "
                + "time_spent_ms, board_after FROM match_moves WHERE match_id=? ORDER BY move_no";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> moves = new ArrayList<>();
                while (rows.next()) {
                    Map<String, Object> move = new LinkedHashMap<>();
                    move.put("moveNo", rows.getInt("move_no"));
                    move.put("playerSlot", rows.getInt("player_slot"));
                    move.put("action", rows.getString("action"));
                    move.put("position", rows.getObject("start_pit"));
                    move.put("direction", rows.getObject("direction"));
                    move.put("gained", rows.getInt("gained"));
                    move.put("timeSpentMs", rows.getInt("time_spent_ms"));
                    move.put("board", rows.getString("board_after"));
                    moves.add(move);
                }
                return moves;
            }
        }
    }

    private List<Map<String, Object>> tinNhan(Connection connection, long matchId) throws SQLException {
        String sql = "SELECT u.username, c.content, c.sent_at FROM chat_messages c "
                + "JOIN users u ON u.id=c.sender_id WHERE c.match_id=? ORDER BY c.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> messages = new ArrayList<>();
                while (rows.next()) {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("username", rows.getString("username"));
                    message.put("content", rows.getString("content"));
                    message.put("sentAt", rows.getTimestamp("sent_at"));
                    messages.add(message);
                }
                return messages;
            }
        }
    }

    public void themChat(long matchId, long senderId, String content) {
        String sql = "INSERT INTO chat_messages (match_id, sender_id, content) VALUES (?, ?, ?)";
        try (Connection connection = DatabaseConnection.layKetNoi();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            statement.setLong(2, senderId);
            statement.setString(3, content);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("Loi khi luu tin nhan chat", e);
        }
    }
}
