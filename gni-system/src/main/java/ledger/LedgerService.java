package ledger;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.RequestType;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import static database.SQLStatements.*;

/**
 * @author Saul
 * @version 1
 */
@RequestMapping("/ledger")
class LedgerService {

    /** Database connection pool containing persistent database connections. */
    private ConnectionPool db;
    /** Prefix used when printing to indicate the message is coming from the Ledger Service. */
    private static final String prefix = "[Ledger]              :";

    /**
     * Constructor.
     */
    LedgerService() {
        db = new ConnectionPool();
    }

    // REST Methods -----------------------------------------------------------------------

    @RequestMapping(value = "/accountNumber", method = RequestMethod.PUT)
    public void newAccountListener(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Account newAccount = gson.fromJson(body, Account.class);
        System.out.printf("%s Received account creation request for customer with name: %s\n", prefix,
                newAccount.getAccountHolderName());

        // Method call
        newAccount = createNewAccount(newAccount);

        if (newAccount != null) {
            System.out.printf("%s Added user %s with accountNumber %s to ledger, sending callback.\n", prefix,
                    newAccount.getAccountHolderName(), newAccount.getAccountNumber());
            callback.reply(gson.toJson(newAccount));
        } else {
            callback.reject("SQLException");
        }
    }

    @RequestMapping(value = "/transaction/in", method = RequestMethod.PUT)
    void incomingTransactionListener(final Callback<String> callback,
                                           final @RequestParam("request") String body) {
        System.out.printf("%s Received an incoming transaction request.\n", prefix);
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);

        // Method call
        transaction = processIncomingTransaction(transaction);

        if (transaction.isSuccessful()) {
            System.out.printf("%s Successfully processed incoming transaction, sending callback.\n", prefix);
            callback.reply(gson.toJson(transaction));
        } else {
            System.out.printf("%s Incoming transaction was not successfull, sending callback.\n", prefix);
            callback.reply(gson.toJson(transaction));
        }
    }

    @RequestMapping(value = "/transaction/out", method = RequestMethod.PUT)
    void outgoingTransactionListener(final Callback<String> callback,
                                           @RequestParam("request") final String requestJson,
                                           @RequestParam("customerId") final String customerId) {
        System.out.printf("%s Received outgoing transaction request for customer %s.\n", prefix, customerId);
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(requestJson, Transaction.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        boolean customerIsAuthorized = getCustomerAuthorization(transaction.getSourceAccountNumber(),
                Long.parseLong(customerId), callbackBuilder);

        // Method call
        transaction = processOutgoingTransaction(transaction, customerIsAuthorized);

        if (transaction.isSuccessful()) {
            System.out.printf("%s Successfully processed outgoing transaction, sending callback.\n", prefix);
            callback.reply(gson.toJson(transaction));
        } else {
            if (!customerIsAuthorized) {
                System.out.printf("%s Customer with id %s is not authorized to make transactions from this account."
                        + " Sending callback.\n", prefix, customerId);
            } else {
                System.out.printf("%s Outgoing transaction was not successfull, sending callback.\n", prefix);
            }
            callback.reply(gson.toJson(transaction));
        }
    }

    @RequestMapping(value = "/data", method = RequestMethod.GET)
    void dataRequestListener(final Callback<String> callback,
                                 final @RequestParam("request") String dataRequestJson) {
        Gson gson = new Gson();
        DataRequest dataRequest = gson.fromJson(dataRequestJson, DataRequest.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.printf("%s Received data request of type %s.\n", prefix, dataRequest.getType().toString());
        if (!getCustomerAuthorization(dataRequest.getAccountNumber(),
                dataRequest.getCustomerId(), callbackBuilder)) {
            callback.reject("Customer not authorized to request data for this accountNumber.");
        }

        // Method call
        DataReply dataReply = processDataRequest(dataRequest);

        if (dataReply != null) {
            System.out.printf("%s Data request successfull, sending callback.\n", prefix);
            callback.reply(gson.toJson(dataReply));
        } else {
            System.out.printf("%s Data request failed, sending rejection.\n", prefix);
            callback.reject("SQLException");
        }
    }

    //------------------------------------------------------------------------------------

    /**
     * Processes a datarequest.
     * The datarequest is either for account information, or a transaction history.
     * @param dataRequest Object representing a DataRequest containing the request information
     */
    DataReply processDataRequest(final DataRequest dataRequest) {
        try {
            switch (dataRequest.getType()) {
                case BALANCE:
                    return processBalanceRequest(dataRequest);
                case TRANSACTIONHISTORY:
                    return processTransactionHistoryRequest(dataRequest);
                case ACCOUNTEXISTS:
                    return processAccountExistsRequest(dataRequest);
                default:
                    return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    DataReply processBalanceRequest(final DataRequest dataRequest) throws SQLException {
        SQLConnection connection = db.getConnection();
        DataReply dataReply = null;
        PreparedStatement ps = connection.getConnection().prepareStatement(getAccountInformation);
        ps.setString(1, dataRequest.getAccountNumber());     // account_number
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String accountNumber = dataRequest.getAccountNumber();
            String name = rs.getString("name");
            double spendingLimit = rs.getDouble("spending_limit");
            double balance = rs.getDouble("balance");
            Account account = new Account(name, spendingLimit, balance);
            account.setAccountNumber(accountNumber);
            dataReply = JSONParser.createJsonReply(dataRequest.getAccountNumber(), dataRequest.getType(), account);
        }

        rs.close();
        ps.close();
        db.returnConnection(connection);

        return dataReply;
    }

    DataReply processTransactionHistoryRequest(final DataRequest dataRequest) throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement ps1 = connection.getConnection().prepareStatement(getIncomingTransactionHistory);
        PreparedStatement ps2 = connection.getConnection().prepareStatement(getOutgoingTransactionHistory);
        ps1.setString(1, dataRequest.getAccountNumber());     // account_number
        ps2.setString(1, dataRequest.getAccountNumber());     // account_number
        ResultSet rs1 = ps1.executeQuery();
        ResultSet rs2 = ps2.executeQuery();

        LinkedList<Transaction> transactions = new LinkedList<>();
        fillTransactionList(transactions, rs1);
        fillTransactionList(transactions, rs2);

        rs1.close();
        rs2.close();
        ps1.close();
        ps2.close();
        db.returnConnection(connection);

        return JSONParser.createJsonReply(dataRequest.getAccountNumber(), dataRequest.getType(), transactions);
    }

    DataReply processAccountExistsRequest(final DataRequest dataRequest) throws SQLException {
        SQLConnection connection = db.getConnection();
        boolean accountExists = false;
        PreparedStatement getAccountNumberCount = connection.getConnection()
                .prepareStatement(SQLStatements.getAccountNumberCount);
        getAccountNumberCount.setString(1, dataRequest.getAccountNumber());
        ResultSet accountNumberCount = getAccountNumberCount.executeQuery();

        if (accountNumberCount.next()) {
            int accountCount = accountNumberCount.getInt(1);
            if (accountCount > 0) {
                accountExists = true;
            }
        }

        accountNumberCount.close();
        db.returnConnection(connection);

        return JSONParser.createJsonReply(dataRequest.getAccountNumber(), dataRequest.getType(), accountExists);
    }

    /**
     * Processes an incoming transaction.
     * Checks if the account exists and then processes the transaction if it does.
     * @param transaction Object representing a Transaction
     */
    Transaction processIncomingTransaction(final Transaction transaction) {
        Account account = getAccountInfo(transaction.getDestinationAccountNumber());

        /*
        // TODO Implement better system for checking destination_account_holder_name
        String calculatedAccountNumber = attemptAccountNumberGeneration(transaction.getDestinationAccountHolderName(),
                Integer.parseInt(transaction.getDestinationAccountNumber().substring(2, 4)));
        if (account != null && transaction.getDestinationAccountNumber().equals(calculatedAccountNumber)) { // TODO update this statement
        */

        if (account != null) {
            // Update the object
            account.processDeposit(transaction);

            // Update the database
            updateBalance(account);

            // Update Transaction log
            transaction.setTransactionID(getHighestTransactionID());
            transaction.generateTimestamp();
            addTransaction(transaction, true);

            transaction.setProcessed(true);
            transaction.setSuccessful(true);
        } else {
            transaction.setProcessed(true);
            transaction.setSuccessful(false);
        }
        return transaction;
    }

    /**
     * Processes an outgoing transaction.
     * Checks if the account making the transaction is allowed to do this. (has a high enough spending limit)
     * @param transaction Object representing a Transaction request.
     */
    Transaction processOutgoingTransaction(final Transaction transaction, final boolean customerIsAuthorized) {

        Account account = getAccountInfo(transaction.getSourceAccountNumber());
        if (account != null && account.withdrawTransactionIsAllowed(transaction) && customerIsAuthorized) {
            // Update the object
            account.processWithdraw(transaction);

            // Update the database
            updateBalance(account);

            /// Update Transaction log
            transaction.setTransactionID(getHighestTransactionID());
            transaction.generateTimestamp();
            addTransaction(transaction, false);

            transaction.setProcessed(true);
            transaction.setSuccessful(true);
        } else {
            transaction.setProcessed(true);
            transaction.setSuccessful(false);
        }
        return transaction;
    }

    /**
     * Creates a new account for a customer and sends the data back to UserService,
     * so that the new account may be properly linked to the customer.
     * @param newAccount Object representing customer information
     * @return account number that was generated
     */
    Account createNewAccount(final Account newAccount) {
        newAccount.setAccountNumber(generateNewAccountNumber(newAccount));
        try {
            SQLConnection connection = db.getConnection();
            long newID = connection.getNextID(getNextAccountID);
            PreparedStatement ps = connection.getConnection().prepareStatement(createNewAccount);
            ps.setLong(1, newID);                               // id
            ps.setString(2, newAccount.getAccountNumber());     // account_number
            ps.setString(3, newAccount.getAccountHolderName()); // name
            ps.setDouble(4, newAccount.getSpendingLimit());     // spending_limit
            ps.setDouble(5, newAccount.getBalance());           // balance

            ps.executeUpdate();
            ps.close();
            db.returnConnection(connection);

            return newAccount;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a new account number based on specific customer information,
     * also checks that the account number doesn't already exist.
     * @param newAccount Account Object containing customer information
     * @return The new account number
     */
    String generateNewAccountNumber(final Account newAccount) {
        int modifier = 0;
        String accountNumber = attemptAccountNumberGeneration(newAccount.getAccountHolderName(), modifier);
        while (modifier < 100 && getAccountInfo(accountNumber) != null) {
            modifier++;
            accountNumber = attemptAccountNumberGeneration(newAccount.getAccountHolderName(), modifier);
        }
        return accountNumber;
    }

    /**
     * Generates a single account number, based on a name and a modifier,
     * which are specified by the calling method.
     * @param name Customer name to be used
     * @param modifier Modifier to be used
     * @return The generated account number
     */
    String attemptAccountNumberGeneration(final String name, final int modifier) {
        String accountNumber = "NL";
        if (modifier < 10) {
            accountNumber += "0";
        }
        accountNumber += modifier + "GNIB";

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((sanitizeName(name) + modifier).getBytes());
            byte[] digest = md.digest();
            for (int i = 0; i < 10; i++) {
                accountNumber += Math.abs(digest[i] % 10);
            }
            return accountNumber;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sanitizes a string.
     * Deleted all spaces, non-letter characters, converts to lower case.
     * @param inputString String to be sanitized
     * @return Sanitized String
     */
    private String sanitizeName(final String inputString) {
        return inputString.replaceAll("[^a-zA-Z]", "").toLowerCase();
    }

    /**
     * Gets all data for a specific account number from the database.
     * @param accountNumber The account number to retrieve the information for
     * @return The account information
     */
    Account getAccountInfo(final String accountNumber) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(getAccountInformation);
            ps.setString(1, accountNumber);     // account_number
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                double spendingLimit = rs.getDouble("spending_limit");
                double balance = rs.getDouble("balance");
                Account account = new Account(name, spendingLimit, balance);
                account.setAccountNumber(accountNumber);

                rs.close();
                ps.close();
                db.returnConnection(connection);
                return account;
            } else {
                rs.close();
                ps.close();
                db.returnConnection(connection);
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Overwrites account information for a specific account,
     * in case a transaction is successfully processed.
     * @param account The account to overwrite, containing the new data
     */
    void updateBalance(final Account account) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateBalance);
            ps.setDouble(1, account.getSpendingLimit());    // spending_limit
            ps.setDouble(2, account.getBalance());          // balance
            ps.setString(3, account.getAccountNumber());    // account_number
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a transaction to either the incoming transaction log, or the outgoing transaction log,
     * depending on the incoming flag.
     * @param transaction Transaction to add
     * @param incoming Incoming flag (true for incoming, false for outgoing)
     */
    void addTransaction(final Transaction transaction, final boolean incoming) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps;
            if (incoming) {
                ps = connection.getConnection().prepareStatement(addIncomingTransaction);
            } else {
                ps = connection.getConnection().prepareStatement(addOutgoingTransaction);
            }

            ps.setLong(1, transaction.getTransactionID());
            ps.setLong(2, transaction.getTimestamp());
            ps.setString(3, transaction.getDestinationAccountNumber());
            ps.setString(4, transaction.getDestinationAccountHolderName());
            ps.setString(5, transaction.getSourceAccountNumber());
            ps.setDouble(6, transaction.getTransactionAmount());
            ps.setString(7, transaction.getDescription());
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Collects the current highest ids from both transaction tables and returns the highest.
     * @return The highest current transaction id
     */
    long getHighestTransactionID() {
        SQLConnection connection = db.getConnection();
        long maxIncoming = connection.getNextID(getHighestIncomingTransactionID);
        long maxOutgoing = connection.getNextID(getHighestOutgoingTransactionID);
        db.returnConnection(connection);

        return Math.max(0, Math.max(maxIncoming, maxOutgoing));
    }


    private boolean getCustomerAuthorization(final String accountNumber, final long customerId,
                                      final CallbackBuilder callbackBuilder) {
        try {
            SQLConnection databaseConnection = db.getConnection();
            PreparedStatement getAccountNumbers = databaseConnection.getConnection()
                                                      .prepareStatement(SQLStatements.getAccountNumbers);
            getAccountNumbers.setLong(1, customerId);
            ResultSet accountRows = getAccountNumbers.executeQuery();
            boolean authorized = false;
            while (accountRows.next() && !authorized) {
                if (accountRows.getString("account_number").equals(accountNumber)) {
                    authorized = true;
                }
            }
            accountRows.close();
            getAccountNumbers.close();
            db.returnConnection(databaseConnection);
            return authorized;
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("SQL exception during authorization check.");
            return false;
        }
    }

    /**
     * Fills a provided list with transactions from a given database query.
     * @param list The list to add the transactions to.
     * @param rs The ResultSet to get the transactions from.
     * @throws SQLException SQLException
     */
    private void fillTransactionList(final List<Transaction> list, final ResultSet rs) throws SQLException {
        while (rs.next()) {
            long id = rs.getLong("id");
            long timestamp = rs.getLong("timestamp");
            String sourceAccount = rs.getString("account_from");
            String destinationAccount = rs.getString("account_to");
            String destinationAccountHolderName = rs.getString("account_to_name");
            String description = rs.getString("description");
            double amount = rs.getDouble("amount");

            list.add(new Transaction(id, timestamp, sourceAccount, destinationAccount, destinationAccountHolderName,
                    description, amount));
        }
    }

    /**
     * Safely shuts down the LedgerService.
     */
    void shutdown() {
        db.close();
    }
}
