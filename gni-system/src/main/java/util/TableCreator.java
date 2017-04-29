package util;

import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author Noel
 * @version 1
 */
public class TableCreator {
    /** Database connection needed to drop/create tables. */
    private static ConnectionPool databaseConnectionPool = new ConnectionPool();
    /** SQL statements to create all necessary tables in the database. */
    private static final String[] CREATE_TABLE_ARRAY = {SQLStatements.createAccountsTable,
                                        SQLStatements.createLedgerTable, SQLStatements.createPinTable,
                                        SQLStatements.createTransactionsInTable,
                                        SQLStatements.createTransactionsOutTable, SQLStatements.createAuthTable,
                                        SQLStatements.createUsersTable};
    /** SQL statements to drop all necessary tables in the database. */
    private static final String[] DROP_TABLE_ARRAY = {SQLStatements.dropAccountsTable, SQLStatements.dropLedgerTable,
                                       SQLStatements.dropPinTable, SQLStatements.dropTransactionsInTable,
                                       SQLStatements.dropTransactionsOutTable, SQLStatements.dropAuthTable,
                                       SQLStatements.dropUsersTable};
    /** Drops all tables and creates new tables to use the system with.
     * @param args Arguments are not used, just there so we can run the main method. */
    public static void main(final String[] args) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            for (String statement : DROP_TABLE_ARRAY) {
                databaseConnection.getConnection().prepareStatement(statement).execute();
            }
            for (String statement : CREATE_TABLE_ARRAY) {
                databaseConnection.getConnection().prepareStatement(statement).execute();
            }
            databaseConnectionPool.returnConnection(databaseConnection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
