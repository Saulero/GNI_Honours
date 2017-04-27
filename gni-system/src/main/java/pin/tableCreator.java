package pin;

import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author noel
 * @version 1
 */
public class tableCreator {
    private final static String createPinTable = "CREATE TABLE `pin` ( `user_id` int(11) NOT NULL, `card_number` varchar(255) NOT NULL, `pin_code` varchar(255) NOT NULL);";
    private static ConnectionPool databaseConnectionPool = new ConnectionPool();
    public static void main(String[] args) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            System.out.println("x");
            databaseConnection.getConnection().prepareStatement(createPinTable).execute();
            System.out.println("y");
            PreparedStatement addPin = databaseConnection.getConnection().prepareStatement(SQLStatements.addPinCard);
            System.out.println("z");
            addPin.setInt(1, 1);
            addPin.setString(2, "123");
            addPin.setString(3, "0000");
            addPin.execute();
            addPin.close();
            PreparedStatement getCustId = databaseConnection.getConnection().prepareStatement(SQLStatements.getCustomerIdFromPinCombination);
            getCustId.setString(1, "123");
            getCustId.setString(2, "0000");
            ResultSet rs = getCustId.executeQuery();
            if (rs.next()) {
                System.out.println(rs.getInt("user_id"));
            }
            rs.close();
            getCustId.close();
            PreparedStatement removePin = databaseConnection.getConnection().prepareStatement(SQLStatements.removePinCard);
            removePin.setInt(1, 1);
            removePin.setString(2, "123");
            removePin.setString(3, "0000");
            removePin.execute();
            removePin.close();
            databaseConnectionPool.returnConnection(databaseConnection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
