package ledger;

import database.ConnectionPool;
import database.SQLConnection;
import io.advantageous.qbit.annotation.Listen;
import io.advantageous.qbit.annotation.OnEvent;
import queue.ServiceManager;
import ui.DataReply;
import ui.DataRequest;
import ui.RequestType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;

import static database.SQLStatements.*;
import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * @author Saul
 */
public class Ledger {

    private ConnectionPool db;

    public Ledger() {
        db = new ConnectionPool();
    }

    /**
     * Listens to USER_CREATION_CHANNEL for new accounts that have to be gernated,
     * generates them and adds their information to the ledger.
     *
     * @param newAccount object containing the account holder name and other information
     */
    @Listen(ServiceManager.USER_CREATION_CHANNEL)
    public void createNewAccount(final Account newAccount) {
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
            // TODO communicate the generated information back to users
        } catch (SQLException e) {
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
            md.update((name + modifier).getBytes());
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

    private void addTransaction(final Transaction transaction, final boolean incoming) {
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
            ps.setString(4, transaction.getSourceAccountNumber());
            ps.setDouble(5, transaction.getTransactionAmount());
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private long getNextTransactionID() {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps1 = connection.getConnection().prepareStatement(getHighestIncomingTransactionID);
            PreparedStatement ps2 = connection.getConnection().prepareStatement(getHighestOutgoingTransactionID);
            ResultSet rs1 = ps1.executeQuery();
            ResultSet rs2 = ps2.executeQuery();

            long current = 0;
            if (rs1.next()) {
                long maxIncoming = rs1.getLong("id");
                if (maxIncoming > current) {
                    current = maxIncoming;
                }
            }

            if (rs2.next()) {
                long maxOutgoing = rs2.getLong("id");
                if (maxOutgoing > current) {
                    current = maxOutgoing;
                }
            }

            rs1.close();
            rs2.close();
            ps1.close();
            ps2.close();
            db.returnConnection(connection);

            return current;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
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
    @OnEvent(value = ServiceManager.INCOMING_TRANSACTION_CHANNEL, consume = true)
    public void processIncomingTransaction(final Transaction transaction) {
        // Check if account info is correct
        Account account = getAccountInfo(transaction.getDestinationAccountNumber());
        // TODO Implement system for checking destination_account_holder_name

        if (account != null) {
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
            serviceContext().send(ServiceManager.INCOMING_TRANSACTION_VERIFICATION_CHANNEL, transaction);
        } else {
            transaction.setProcessed(true);
            transaction.setSuccessful(false);
            serviceContext().send(ServiceManager.INCOMING_TRANSACTION_VERIFICATION_CHANNEL, transaction);
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
    @OnEvent(value = ServiceManager.OUTGOING_TRANSACTION_CHANNEL, consume = true)
    public void processOutgoingTransaction(final Transaction transaction) {
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
            serviceContext().send(ServiceManager.INCOMING_TRANSACTION_VERIFICATION_CHANNEL, transaction);
        } else {
            transaction.setProcessed(true);
            transaction.setSuccessful(false);
            serviceContext().send(ServiceManager.INCOMING_TRANSACTION_VERIFICATION_CHANNEL, transaction);
        }
    }

    /**
     * Listens on DATA_REQUEST_CHANNEL for customer data requests.
     * If the request is a balance or transaction request the method gets this data from the database and sends
     * it back in a dataReply object.
     *
     * @param dataRequest DataRequest object containing the customer data request
     */
    @Listen(ServiceManager.DATA_REQUEST_CHANNEL)
    public void processDataRequest(final DataRequest dataRequest) {
        RequestType requestType = dataRequest.getType();
        if (requestType == RequestType.BALANCE) {
            try {
                SQLConnection connection = db.getConnection();
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
                    DataReply dataReply = new DataReply(accountNumber, requestType, account);
                    serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);
                }
                // TODO What if fails
                rs.close();
                ps.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
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
                serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);

                rs1.close();
                rs2.close();
                ps1.close();
                ps2.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void fillTransactionList(final LinkedList<Transaction> list, final ResultSet rs) throws SQLException {
        while (rs.next()) {
            long id = rs.getLong("id");
            long timestamp = rs.getLong("timestamp");
            String sourceAccount = rs.getString("account_to");
            String destinationAccount = rs.getString("account_from");
            double amount = rs.getDouble("amount");

            list.add(new Transaction(id, timestamp, sourceAccount, destinationAccount, amount));
        }
    }
}
