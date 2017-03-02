package database;

import java.sql.*;

import static database.Variables.PASSWORD;
import static database.Variables.URL;
import static database.Variables.USERNAME;

/**
 * @author Saul
 */
public class SQLConnection {

    /** SQL Connection. */
    private Connection connection;

    /**
     * Constructor.
     * Initiates the new SQLConnection and immediately connects to the database.
     */
    public SQLConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the SQL Connection.
     * @return SQL Connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Gets the current highest ID of some table, specified in the given query,
     * then returns the next id to be used.
     * @param query The query to run.
     * @return The next id to be used.
     */
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

    /**
     * Closes the SQL Connection.
     */
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
