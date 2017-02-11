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

    public ResultSet excecuteQuery(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            return statement.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int excecuteUpdate(String query) {
        try {
            PreparedStatement statement = connection.prepareStatement(query);
            return statement.executeUpdate();
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
