import io.advantageous.qbit.annotation.Listen;
import io.advantageous.qbit.annotation.OnEvent;

import java.util.HashMap;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 4-2-17.
 * Keeps track of account numbers and their respective account holder and balance.
 */
class LedgerService {
    private HashMap<String, Double> ledger;

    LedgerService() {
        //TODO make ledger database and convert functions using hashmap
        this.ledger = new HashMap<String, Double>();
    }

    /**
     * Listens to USER_CREATION_CHANNEL for new customers and adds their account number to the ledger.
     * @param customer customer object containing the customers name and accountnumber
     */
    @Listen(ServiceManager.USER_CREATION_CHANNEL)
    void process_new_user(final Customer customer) {
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
    void process_transaction(final Transaction transaction) {
        String accountNumber = transaction.getSourceAccountNumber();
        if(this.ledger.keySet().contains(accountNumber)) {
            //TODO implement database function for spending limit
            double spendingLimit = this.ledger.get(accountNumber);
            if (spendingLimit - transaction.getTransactionAmount() < 0) {
                transaction.setProcessed(true);
                System.out.printf("Ledger: Transaction number %s failed due to insufficient balance.\n",
                        transaction.getTransactionNumber());
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
    void process_data_request(final DataRequest dataRequest) {
        DataRequest.requestType requestType = dataRequest.getType();
        if (requestType == DataRequest.requestType.BALANCE) {
            String accountNumber = dataRequest.getAccountNumber();
            DataReply dataReply = new DataReply(accountNumber, requestType, "" + this.ledger.get(accountNumber));
            serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL,  dataReply);
        }
        else if (requestType == DataRequest.requestType.TRANSACTIONHISTORY) {
            //TODO fetch transaction history
            String transactionHistory = "Dummy history";
            String accountNumber = dataRequest.getAccountNumber();
            DataReply dataReply = new DataReply(accountNumber, requestType, transactionHistory);
            serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);
        }
    }

    void printLedger() {
        for (String key : this.ledger.keySet()) {
            System.out.printf("Account: %s Balance: %f", key, this.ledger.get(key));
        }
    }
}

class Transaction {
    private String sourceAccountNumber;
    private double transactionAmount;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private String transactionNumber;
    private boolean processed;
    private boolean successfull;

    Transaction(String sourceAccountNumber, double transactionAmount, String destinationAccountNumber,
                String destinationAccountHolderName, String transactionNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
        this.transactionAmount = transactionAmount;
        this.destinationAccountNumber = destinationAccountNumber;
        this.destinationAccountHolderName = destinationAccountHolderName;
        this.transactionNumber = transactionNumber;
        this.processed = false;
        this.successfull = false;
    }

    String getSourceAccountNumber() { return this.sourceAccountNumber; }

    double getTransactionAmount() { return this.transactionAmount; }

    String getDestinationAccountNumber() { return this.destinationAccountNumber; }

    String getDestinationAccountHolderName() { return this.destinationAccountHolderName; }

    String getTransactionNumber() { return this.transactionNumber; }

    boolean getProcessed() { return this.processed; }

    void setProcessed(boolean processed) { this.processed = processed; }

    boolean getSuccessfull() { return this.successfull; }

    void setSuccessfull(boolean successfull) { this.successfull = successfull; }
}
