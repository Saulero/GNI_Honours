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
        while (modifier < 100 && accountNumberExists(accountNumber)) {
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

    public boolean accountNumberExists(final String accountNumber) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(getAccountInformation);
            ps.setString(1, accountNumber);     // account_number
            ResultSet rs = ps.executeQuery();

            boolean res = rs.next();
            rs.close();
            ps.close();
            db.returnConnection(connection);
            return res;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Listens for transactions on TRANSACTION_PROCESSING_CHANNEL when a transaction requires processing
     * the ledger checks if the accounts spending limit is not exceeded by the transaction, if it is not
     * the transaction variable successfull will be set to true and the ledger will apply the transaction.
     * If the spending limit is exceeded the ledger will not apply the transaction and the successfull variable
     * will be set to false. The transaction is then sent back to TransactionDispatchService
     * through TRANSACTION_VERIFICATION_CHANNEL.
     * @param transaction Transaction object to perform the transaction
     */
    @OnEvent(value = ServiceManager.TRANSACTION_PROCESSING_CHANNEL, consume = true)
    public void processTransaction(final Transaction transaction) {
        // TODO Discuss ths method
        /*
        String accountNumber = transaction.getSourceAccountNumber();
        if(this.ledger.keySet().contains(accountNumber)) {
            //TODO implement database function for spending limit
            double spendingLimit = this.ledger.get(accountNumber);
            if (spendingLimit - transaction.getTransactionAmount() < 0) {
                transaction.setProcessed(true);
                System.out.printf("Ledger: Transaction number %s failed due to insufficient balance.\n",
                        transaction.getTransactionID());
                serviceContext().send(ServiceManager.TRANSACTION_VERIFICATION_CHANNEL, transaction);
            } else {
                double new_balance = this.ledger.get(accountNumber) - transaction.getTransactionAmount();
                this.ledger.put(accountNumber, new_balance);
                System.out.printf("Ledger: Processed transaction, Account number: %s, new balance: %f\n\n",
                        accountNumber, new_balance);
                transaction.setProcessed(true);
                transaction.setSuccessfull(true);
                serviceContext().send(ServiceManager.TRANSACTION_VERIFICATION_CHANNEL, transaction);
            }
        }*/
    }

    /**
     * Listens on DATA_REQUEST_CHANNEL for customer data requests.
     * If the request is a balance or transaction request the method gets this data from the database and sends
     * it back in a dataReply object.
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
                PreparedStatement ps = connection.getConnection().prepareStatement(getTransactionHistory);
                ps.setString(1, dataRequest.getAccountNumber());     // account_number
                ps.setString(2, dataRequest.getAccountNumber());     // account_number
                ResultSet rs = ps.executeQuery();

                LinkedList<Transaction> transactions = new LinkedList<Transaction>();
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long timestamp = rs.getLong("timestamp");
                    String sourceAccount = rs.getString("source_account");
                    String destinationAccount = rs.getString("destination_account");
                    // TODO update DB
                    String name = rs.getString("destination_account_holder_name");
                    double amount = rs.getDouble("amount");

                    transactions.add(new Transaction(id, timestamp, sourceAccount, destinationAccount, name, amount));
                }

                DataReply dataReply = new DataReply(dataRequest.getAccountNumber(), requestType, transactions);
                serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);
                // TODO What if fails
                rs.close();
                ps.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
