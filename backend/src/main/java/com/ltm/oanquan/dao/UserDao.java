package com.ltm.oanquan.dao;

import com.ltm.oanquan.model.Users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Truy cap bang users bang JDBC thuan. Moi cau lenh deu dung PreparedStatement (chong SQL injection). */
public class UserDao {

    private static final int MA_LOI_TRUNG_KHOA = 1062;   // MySQL: Duplicate entry

    /** Tra ve null neu khong tim thay. */
    public Users timTheoTen(String tenDangNhap) {
        return timMot("SELECT id, username, password_hash, points, rating FROM users WHERE username = ?",
                tenDangNhap);
    }

    /** Tra ve null neu khong tim thay. */
    public Users timTheoId(long id) {
        return timMot("SELECT id, username, password_hash, points, rating FROM users WHERE id = ?", id);
    }

    public List<Users> bangRanking(int gioiHan) {
        int limit = Math.max(1, Math.min(gioiHan, 100));
        String sql = "SELECT id, username, points, rating FROM users "
                + "WHERE username <> '__bot__' ORDER BY points DESC, rating DESC, id ASC LIMIT ?";
        try (Connection kn = DatabaseConnection.layKetNoi();
             PreparedStatement ps = kn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet kq = ps.executeQuery()) {
                List<Users> users = new ArrayList<>();
                while (kq.next()) {
                    users.add(new Users(kq.getLong("id"), kq.getString("username"),
                            null, kq.getInt("points"), kq.getInt("rating")));
                }
                return users;
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi truy van bang xep hang", e);
        }
    }

    /** Them nguoi dung moi, tra ve id vua tao. */
    public long them(String tenDangNhap, String matKhauBam) throws UsernameAlreadyExistsException {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection kn = DatabaseConnection.layKetNoi();
             PreparedStatement ps = kn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tenDangNhap);
            ps.setString(2, matKhauBam);
            ps.executeUpdate();
            try (ResultSet khoa = ps.getGeneratedKeys()) {
                if (khoa.next()) {
                    return khoa.getLong(1);
                }
                throw new DaoException("Khong lay duoc id vua tao", null);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MA_LOI_TRUNG_KHOA) {
                throw new UsernameAlreadyExistsException(tenDangNhap);
            }
            throw new DaoException("Loi khi them nguoi dung", e);
        }
    }

    private Users timMot(String sql, Object thamSo) {
        try (Connection kn = DatabaseConnection.layKetNoi();
             PreparedStatement ps = kn.prepareStatement(sql)) {
            ps.setObject(1, thamSo);
            try (ResultSet kq = ps.executeQuery()) {
                if (!kq.next()) {
                    return null;
                }
                return docNguoiDung(kq);
            }
        } catch (SQLException e) {
            throw new DaoException("Loi khi truy van nguoi dung", e);
        }
    }

    private Users docNguoiDung(ResultSet kq) throws SQLException {
        return new Users(kq.getLong("id"), kq.getString("username"),
                kq.getString("password_hash"), kq.getInt("points"), kq.getInt("rating"));
    }
}