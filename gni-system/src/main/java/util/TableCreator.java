package util;

import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;

import java.sql.SQLException;

/**
 * @author Noel & Saul
 * @version 2
 */
public class TableCreator {

    /** Database connection needed to drop/create tables. */
    private static ConnectionPool databaseConnectionPool = new ConnectionPool();

    /** SQL statements to create all necessary tables in the database. */
    private static final String[] CREATE_TABLE_ARRAY = {
            SQLStatements.createAccountsTable,
            SQLStatements.createLedgerTable,
            SQLStatements.createPinTable,
            SQLStatements.createTransactionsInTable,
            SQLStatements.createTransactionsOutTable,
            SQLStatements.createAuthTable,
            SQLStatements.createUsersTable,
            SQLStatements.createRequestLogTable,
            SQLStatements.createErrorLogTable,
            SQLStatements.createAdminTable};

    /** SQL statements to drop all necessary tables in the database. */
    private static final String[] DROP_TABLE_ARRAY = {
            SQLStatements.dropAccountsTable,
            SQLStatements.dropLedgerTable,
            SQLStatements.dropPinTable,
            SQLStatements.dropTransactionsInTable,
            SQLStatements.dropTransactionsOutTable,
            SQLStatements.dropAuthTable,
            SQLStatements.dropUsersTable,
            SQLStatements.dropRequestLogTable,
            SQLStatements.dropErrorLogTable,
            SQLStatements.dropAdminTable};

    /** SQL statements to truncate all tables in the database. */
    private static final String[] TRUNCATE_ARRAY = {
            SQLStatements.truncateAccountsTable,
            SQLStatements.truncateLedgerTable,
            SQLStatements.truncatePinTable,
            SQLStatements.truncateTransactionsInTable,
            SQLStatements.truncateTransactionsOutTable,
            SQLStatements.truncateAuthTable,
            SQLStatements.truncateUsersTable,
            SQLStatements.truncateRequestLogTable,
            SQLStatements.truncateErrorLogTable,
            SQLStatements.truncateAdminTable};

    /** Drops all tables and creates new tables to use the system with.
     * @param args Arguments are not used, just there so we can run the main method. */
    public static void main(final String[] args) {
        createNewTables();
        createDefaultAdmin();
    }

    private static void createDefaultAdmin() {
        executeStatements(new String[] {
                SQLStatements.createDefaultAdmin,
                SQLStatements.addAdminAuthenticationData});
        grantDefaultAdminPermissions();
    }

    private static void grantDefaultAdminPermissions() {
        executeStatements(new String[] {
                SQLStatements.grantGetBalance,
                SQLStatements.grantGetTransactionOverview,
                /*SQLStatements.grantGetUserAccess,*/
                SQLStatements.grantGetBankAccountAccess,
                SQLStatements.grantGetOverdraftLimit,
                SQLStatements.grantSimulateTime,
                SQLStatements.grantReset,
                SQLStatements.grantGetDate,
                SQLStatements.grantGetEventLogs});
    }

    private static void createNewTables() {
        executeStatements(DROP_TABLE_ARRAY);
        executeStatements(CREATE_TABLE_ARRAY);
    }

    private static void executeStatements(final String[] statements) {
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

    public static void truncateTables() {
        executeStatements(TRUNCATE_ARRAY);
        createDefaultAdmin();
    }
}
