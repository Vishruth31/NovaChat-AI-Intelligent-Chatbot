package chatbot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/personal_library";
    private static final String USER = "root";
    private static final String PASSWORD = "chatbot@123";

    public static Connection getConnection() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            conn.setAutoCommit(true); // ensures permanent storage
            System.out.println("✅ Database connected successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("❌ Failed to connect to database.");
        }
        return conn;
    }
}
