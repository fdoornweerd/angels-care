package org.angelscare.management;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:" + getDbPath();

    private static String getDbPath() {
        String userHome = System.getProperty("user.home");
        File appDataDir = new File(userHome, "AngelsCareData");
        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
        }
        return new File(appDataDir, "angels-care.db").getAbsolutePath();
    }

    public static String testConnection() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO students (name) VALUES ('Test Student')");
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM students");
            int count = rs.getInt("count");
            return "SQLite connected. Rows in students table: " + count + "\n\n SUCCESS!";
        } catch (SQLException e) {
            return "SQLite connection failed: " + e.getMessage();
        }
    }
}