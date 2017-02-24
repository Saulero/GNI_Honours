package database;

import java.sql.*;

import static database.Variables.PASSWORD;
import static database.Variables.URL;
import static database.Variables.USERNAME;

/**
 * @author Saul
 */
public class SQLConnection {

    private Connection connection;

    public SQLConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public long getNextID(final String query) {
        try {
            PreparedStatement ps = connection.prepareStatement(query);
            ResultSet rs = ps.executeQuery();
            if (rs != null) {
                long id = -2;
                if (rs.next()) {
                    id = rs.getLong(1);
                }
                rs.close();
                ps.close();
                return id + 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
