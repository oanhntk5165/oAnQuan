package com.ltm.oanquan.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Tạo kết nối MySQL từ application.properties
 */
public final class DatabaseConnection {

    private static Properties cauHinh;

    private DatabaseConnection() {
    }

    private static synchronized Properties layCauHinh() {
        if (cauHinh == null) {
            Properties p = new Properties();
            napTuTaiNguyen(p, "application.properties");
            napTuFile(p, Path.of("src", "main", "resources", "application.properties"));
            if (p.getProperty("url") == null || p.getProperty("user") == null
                    || p.getProperty("password") == null) {
                throw new DaoException(
                        "Thiếu cấu hình CSDL: cần url, user, password trong application.properties", null);
            }
            cauHinh = p;
        }
        return cauHinh;
    }

    private static void napTuTaiNguyen(Properties properties, String fileName) {
        try (InputStream in = DatabaseConnection.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in != null)
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DaoException("Không đọc được cấu hình " + fileName, e);
        }
    }

    private static void napTuFile(Properties properties, Path file) {
        if (!Files.exists(file))
            return;
        try (InputStream in = new FileInputStream(file.toFile());
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new DaoException("Không đọc được cấu hình " + file, e);
        }
    }

    public static Connection layKetNoi() throws SQLException {
        Properties p = layCauHinh();
        return DriverManager.getConnection(
                p.getProperty("url"), p.getProperty("user"), p.getProperty("password"));
    }
}