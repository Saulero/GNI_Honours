package ledger;

import io.advantageous.qbit.annotation.Listen;
import io.advantageous.qbit.annotation.OnEvent;
import queue.ServiceManager;
import ui.DataReply;
import ui.DataRequest;
import ui.RequestType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * @author Saul
 */
public class Ledger {

    private HashMap<String, Double> ledger;

    public Ledger() {
        //TODO make ledger database and convert functions using hashmap
        this.ledger = new HashMap<String, Double>();
    }

    /**
     * Listens to USER_CREATION_CHANNEL for new accounts that have to be gernated,
     * generates them and adds their information to the ledger.
     * @param newAccount object containing the account holder name and other information
     */
    @Listen(ServiceManager.USER_CREATION_CHANNEL)
    public void createNewAccount(final NewAccount newAccount) {
        // TODO generate account number and process the data in the database
        /* if (!this.ledger.keySet().contains(customer.getAccountNumber())) {
            this.ledger.put(customer.getAccountNumber(), 0.0);
            System.out.printf("Ledger: Added user %s %s to ledger\n\n", customer.getName(), customer.getSurname());
        } /*
        //TODO communicate the generated information back to users
    }

    public String generateNewAccountNumber(final NewAccount newAccount) {
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
        // TODO Return true if it exists, using getAccountInformation statement
        return accountNumber == null;
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
        }
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
            String accountNumber = dataRequest.getAccountNumber();
            DataReply dataReply = new DataReply(accountNumber, requestType, "" + this.ledger.get(accountNumber));
            serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL,  dataReply);
        }
        else if (requestType == RequestType.TRANSACTIONHISTORY) {
            //TODO fetch transaction history
            String transactionHistory = "Dummy history";
            String accountNumber = dataRequest.getAccountNumber();
            DataReply dataReply = new DataReply(accountNumber, requestType, transactionHistory);
            serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);
        }
    }

    public void printLedger() {
        for (String key : this.ledger.keySet()) {
            System.out.printf("Account: %s Balance: %f", key, this.ledger.get(key));
        }
    }
}
