package util;

import database.*;

import java.sql.SQLException;

/**
 * @author Noel & Saul
 * @version 2
 */
public class TableCreator {
    /** Database connection needed to drop/create tables. */
    private static ConnectionPool databaseConnectionPool = new ConnectionPool();

    /** SQL statements to create all necessary tables in the database. */
    private static final String[] CREATE_TABLE_ARRAY = {SQLStatements.createAccountsTable,
            SQLStatements.createLedgerTable, SQLStatements.createPinTable,
            SQLStatements.createTransactionsInTable,
            SQLStatements.createTransactionsOutTable, SQLStatements.createAuthTable,
            SQLStatements.createUsersTable, SQLStatements.createRequestLogTable, SQLStatements.createErrorLogTable};

    /** SQL statements to drop all necessary tables in the database. */
    private static final String[] DROP_TABLE_ARRAY = {SQLStatements.dropAccountsTable, SQLStatements.dropLedgerTable,
            SQLStatements.dropPinTable, SQLStatements.dropTransactionsInTable,
            SQLStatements.dropTransactionsOutTable, SQLStatements.dropAuthTable,
            SQLStatements.dropUsersTable, SQLStatements.dropRequestLogTable, SQLStatements.dropErrorLogTable};

    /** SQL statements to truncate all tables in the database. */
    private static final String[] TRUNCATE_ARRAY = {SQLStatements.truncateAccountsTable,
            SQLStatements.truncateLedgerTable, SQLStatements.truncatePinTable,
            SQLStatements.truncateTransactionsInTable, SQLStatements.truncateTransactionsOutTable,
            SQLStatements.truncateAuthTable, SQLStatements.truncateUsersTable, SQLStatements.truncateRequestLogTable,
            SQLStatements.truncateErrorLogTable};

    /** Drops all tables and creates new tables to use the system with.
     * @param args Arguments are not used, just there so we can run the main method. */
    public static void main(final String[] args) {
        createNewTables();
    }

    public static void truncateTables() {
        executeStatements(TRUNCATE_ARRAY);
    }

    public static void dropTables() {
        executeStatements(DROP_TABLE_ARRAY);
    }

    public static void createTables() {
        executeStatements(CREATE_TABLE_ARRAY);
    }

    public static void createNewTables() {
        dropTables();
        createTables();
    }

    public static void executeStatements(String[] statements) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            for (String statement : statements) {
                databaseConnection.getConnection().prepareStatement(statement).execute();
            }
            databaseConnectionPool.returnConnection(databaseConnection);
        }  catch (SQLException e) {
            e.printStackTrace();
        }
    }
}