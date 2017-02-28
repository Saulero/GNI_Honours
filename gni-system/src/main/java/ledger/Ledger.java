package ledger;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.RequestType;
import util.JSONParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;

import static database.SQLStatements.*;

/**
 * @author Saul
 * @version 1
 */
@RequestMapping("/ledger")
public class Ledger {

    private ConnectionPool db;

    public Ledger() {
        db = new ConnectionPool();
    }

    /**
     * Listens to USER_CREATION_CHANNEL for new customers and adds their account number to the ledger.
     * @param customer customer object containing the customers name and accountnumber
     */
    @RequestMapping(value = "/accountNumber", method = RequestMethod.PUT)
    public void createNewAccount(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Account newAccount = gson.fromJson(body, Account.class);
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
            System.out.printf("Ledger: Added users %s with accountNumber %s to ledger\n\n",
                    newAccount.getAccountHolderName(), newAccount.getAccountNumber());
            callback.reply(gson.toJson(newAccount));
        } catch (SQLException e) {
            callback.reject(e.getMessage());
            e.printStackTrace();
        }
    }

    public String generateNewAccountNumber(final Account newAccount) {
        int modifier = 0;
        String accountNumber = attemptAccountNumberGeneration(newAccount.getAccountHolderName(), modifier);
        while (modifier < 100 && getAccountInfo(accountNumber) != null) {
            modifier++;
            accountNumber = attemptAccountNumberGeneration(newAccount.getAccountHolderName(), modifier);
        }
        return accountNumber;
    }

    public String attemptAccountNumberGeneration(final String name, final int modifier) {
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

    private String sanitizeName(final String inputString) {
        return inputString.replaceAll("[^a-zA-Z]", "").toLowerCase();
    }

    public Account getAccountInfo(final String accountNumber) {
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

    public void updateBalance(final Account account) {
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

    public void addTransaction(final Transaction transaction, final boolean incoming) {
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

    public long getNextTransactionID() {
        SQLConnection connection = db.getConnection();
        long maxIncoming = connection.getNextID(getHighestIncomingTransactionID);
        long maxOutgoing = connection.getNextID(getHighestOutgoingTransactionID);
        db.returnConnection(connection);

        return Math.max(0, Math.max(maxIncoming, maxOutgoing));
    }

    /**
     * Listens for transactions on INCOMING_TRANSACTION_CHANNEL when a transaction requires processing
     * the ledger checks if the accounts spending limit is not exceeded by the transaction, if it is not
     * the transaction variable successfull will be set to true and the ledger will apply the transaction.
     * If the spending limit is exceeded the ledger will not apply the transaction and the successfull variable
     * will be set to false. The transaction is then sent back to TransactionDispatchService
     * through INCOMING_TRANSACTION_VERIFICATION_CHANNEL.
     *
     * @param transaction Transaction object to perform the transaction
     */
    @RequestMapping(value = "/transaction/in", method = RequestMethod.PUT)
    public void processIncomingTransaction(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        System.out.println("Ledger: received an incoming transaction.");
        Transaction transaction = gson.fromJson(body, Transaction.class);
        // Check if account info is correct
        Account account = getAccountInfo(transaction.getDestinationAccountNumber());
        // TODO Implement better system for checking destination_account_holder_name
        String calculatedAccountNumber = attemptAccountNumberGeneration(transaction.getDestinationAccountHolderName(),
                                        Integer.parseInt(transaction.getDestinationAccountNumber().substring(2, 4)));

        if (account != null && transaction.getDestinationAccountNumber().equals(calculatedAccountNumber)) {
            // Update the object
            account.processDeposit(transaction);

            // Update the database
            updateBalance(account);

            // Update Transaction log
            transaction.setTransactionID(getNextTransactionID());
            transaction.generateTimestamp();
            addTransaction(transaction, true);

            transaction.setProcessed(true);
            transaction.setSuccessful(true);
            System.out.println("Ledger: Successfully processed the transaction.");
            callback.reply(gson.toJson(transaction));
        } else {
            transaction.setProcessed(true);
            transaction.setSuccessful(false);
            System.out.println("Ledger: Transaction was not successful.");
            callback.reply(gson.toJson(transaction));
        }
    }

    /**
     * Listens for transactions on OUTGOING_TRANSACTION_CHANNEL when a transaction requires processing
     * the ledger checks if the accounts spending limit is not exceeded by the transaction, if it is not
     * the transaction variable successfull will be set to true and the ledger will apply the transaction.
     * If the spending limit is exceeded the ledger will not apply the transaction and the successfull variable
     * will be set to false. The transaction is then sent back to TransactionDispatchService
     * through OUTGOING_TRANSACTION_VERIFICATION_CHANNEL.
     *
     * @param transaction Transaction object to perform the transaction
     */
    @RequestMapping(value = "/transaction/out", method = RequestMethod.PUT)
    public void processOutgoingTransaction(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);
        // Check if spending_limit allows for the transaction
        Account account = getAccountInfo(transaction.getSourceAccountNumber());

        if (account.withdrawTransactionIsAllowed(transaction)) {
            // Update the object
            account.processWithdraw(transaction);

            // Update the database
            updateBalance(account);

            /// Update Transaction log
            transaction.setTransactionID(getNextTransactionID());
            transaction.generateTimestamp();
            addTransaction(transaction, false);

            transaction.setProcessed(true);
            transaction.setSuccessful(true);
            callback.reply(gson.toJson(transaction));
        } else {
            transaction.setProcessed(true);
            transaction.setSuccessful(false);
            callback.reply(gson.toJson(transaction));
        }
    }

    /**
     * Listens on DATA_REQUEST_CHANNEL for customer data requests.
     * If the request is a balance or transaction request the method gets this data from the database and sends
     * it back in a dataReply object.
     *
     * @param dataRequest DataRequest object containing the customer data request
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        DataRequest dataRequest = gson.fromJson(body, DataRequest.class);
        RequestType requestType = dataRequest.getType();
        if (requestType == RequestType.BALANCE) {
            try {
                SQLConnection connection = db.getConnection();
                PreparedStatement ps = connection.getConnection().prepareStatement(getAccountInformation);
                ps.setString(1, dataRequest.getAccountNumber());     // account_number
                ResultSet rs = ps.executeQuery();

                DataReply dataReply = null;
                if (rs.next()) {
                    String accountNumber = dataRequest.getAccountNumber();
                    String name = rs.getString("name");
                    double spendingLimit = rs.getDouble("spending_limit");
                    double balance = rs.getDouble("balance");
                    Account account = new Account(name, spendingLimit, balance);
                    account.setAccountNumber(accountNumber);
                    dataReply = JSONParser.createJsonReply(accountNumber, requestType, account);
                    callback.reply(gson.toJson(dataReply));
                }

                rs.close();
                ps.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
                callback.reject(e.getMessage());
                e.printStackTrace();
            }
        } else if (requestType == RequestType.TRANSACTIONHISTORY) {
            try {
                SQLConnection connection = db.getConnection();
                PreparedStatement ps1 = connection.getConnection().prepareStatement(getIncomingTransactionHistory);
                PreparedStatement ps2 = connection.getConnection().prepareStatement(getOutgoingTransactionHistory);
                ps1.setString(1, dataRequest.getAccountNumber());     // account_number
                ps2.setString(1, dataRequest.getAccountNumber());     // account_number
                ResultSet rs1 = ps1.executeQuery();
                ResultSet rs2 = ps2.executeQuery();

                LinkedList<Transaction> transactions = new LinkedList<Transaction>();
                fillTransactionList(transactions, rs1);
                fillTransactionList(transactions, rs2);

                DataReply dataReply = new DataReply(dataRequest.getAccountNumber(), requestType, transactions);
                callback.reply(gson.toJson(dataReply));

                rs1.close();
                rs2.close();
                ps1.close();
                ps2.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
                callback.reject(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void fillTransactionList(final LinkedList<Transaction> list, final ResultSet rs) throws SQLException {
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

    public void shutdown() {
        db.close();
    }
}
