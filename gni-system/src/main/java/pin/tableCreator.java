package pin;

import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author noel
 */
public class tableCreator {
    private static ConnectionPool databaseConnectionPool = new ConnectionPool();
    /** SQL statements to create all necessary tables in the database. */
    private static final String[] CREATE_TABLE_ARRAY = {SQLStatements.createAccountsTable, SQLStatements.createLedgerTable,
                                        SQLStatements.createPinTable, SQLStatements.createTransactionsInTable,
                                        SQLStatements.createTransactionsOutTable, SQLStatements.createAuthTable,
                                        SQLStatements.createUsersTable};
    /** SQL statements to drop all necessary tables in the database. */
    private static final String[] DROP_TABLE_ARRAY = {SQLStatements.dropAccountsTable, SQLStatements.dropLedgerTable,
                                       SQLStatements.dropPinTable, SQLStatements.dropTransactionsInTable,
                                       SQLStatements.dropTransactionsOutTable, SQLStatements.dropAuthTable,
                                       SQLStatements.dropUsersTable};
    public static void main(String[] args) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            for (String statement : DROP_TABLE_ARRAY) {
                databaseConnection.getConnection().prepareStatement(statement).execute();
            }
            for (String statement : CREATE_TABLE_ARRAY) {
                databaseConnection.getConnection().prepareStatement(statement).execute();
            }

            /*//databaseConnection.getConnection().prepareStatement(SQLStatements.createAuthTable).execute();
            PreparedStatement addPin = databaseConnection.getConnection().prepareStatement(SQLStatements.addPinCard);
            addPin.setInt(1, 6);
            addPin.setString(2, "730");
            addPin.setString(3, "8888");
            addPin.execute();
            addPin.close();
            /*
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
            removePin.close();*/
            /*PreparedStatement getAuth = databaseConnection.getConnection().prepareStatement(selectAuth);
            ResultSet rs = getAuth.executeQuery();
            while (rs.next()) {
                System.out.println();
            }*/
            databaseConnectionPool.returnConnection(databaseConnection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
