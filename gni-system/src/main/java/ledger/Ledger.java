package ledger;

import io.advantageous.qbit.annotation.Listen;
import io.advantageous.qbit.annotation.OnEvent;
import queue.ServiceManager;
import ui.DataReply;
import ui.DataRequest;
import ui.RequestType;
import users.Customer;

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
     * Listens to USER_CREATION_CHANNEL for new customers and adds their account number to the ledger.
     * @param customer customer object containing the customers name and accountnumber
     */
    @Listen(ServiceManager.USER_CREATION_CHANNEL)
    public void process_new_user(final Customer customer) {
        if (!this.ledger.keySet().contains(customer.getAccountNumber())){
            this.ledger.put(customer.getAccountNumber(), 0.0);
            System.out.printf("Ledger: Added user %s %s to ledger\n\n", customer.getName(), customer.getSurname());
        }
        //TODO code to process what happens when the accountnumber already exists in the ledger.
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
    public void process_transaction(final Transaction transaction) {
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
    public void process_data_request(final DataRequest dataRequest) {
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
