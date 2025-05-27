package dacs1.v2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBUtil {
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/userdb";
        String user = "root";
        String pass = "1234";
        return DriverManager.getConnection(url, user, pass);
    }
}