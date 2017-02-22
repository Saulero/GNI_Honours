package ledger;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.*;
import io.advantageous.qbit.reactive.Callback;
import queue.ServiceManager;
import util.*;

import java.util.HashMap;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * @author Saul
 */
@RequestMapping("/ledger")
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
    @RequestMapping(value = "/accountNumber", method = RequestMethod.PUT)
    public void processAccountNumberRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Customer customer = gson.fromJson(body, Customer.class);
        //TODO properly set accountnumber, will happen when merging with ledger branch.
        String newAccountNumber = "NL55GNIB0938723334";
        Customer reply = Util.createJsonCustomer(customer.getName(), customer.getSurname(), newAccountNumber,
                                                true);
        System.out.printf("Ledger: Added user %s with accountNumber %s to ledger\n\n", reply.getSurname(),
                reply.getAccountNumber());
        callback.reply(gson.toJson(reply));
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
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransaction(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);
        String accountNumber = transaction.getSourceAccountNumber();
        //TODO generate transactionId
        long transactionId = 134567890;
        Transaction reply = Util.createJsonTransaction(transactionId, transaction.getSourceAccountNumber(),
                                                       transaction.getDestinationAccountNumber(),
                                                       transaction.getDestinationAccountHolderName(),
                                                       transaction.getTransactionAmount(), true,
                                                       false);
        boolean accountInLedger = true;
        if (accountInLedger) {
            //TODO implement database function for spending limit
            double spendingLimit = 10000;
            if (spendingLimit - transaction.getTransactionAmount() < 0) {
                System.out.printf("Ledger: Transaction number %s failed due to insufficient balance.\n",
                        transaction.getTransactionID());
                callback.reply(gson.toJson(reply));
            } else {
                //TODO process transaction in database
                System.out.printf("Ledger: Processed transaction, Account number: %s\n\n", accountNumber);
                reply.setSuccessfull(true);
                callback.reply(gson.toJson(reply));
            }
        }
    }

    /**
     * Listens on DATA_REQUEST_CHANNEL for customer data requests.
     * If the request is a balance or transaction request the method gets this data from the database and sends
     * it back in a dataReply object.
     * @param dataRequest DataRequest object containing the customer data request
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        System.out.println("Ledger: Called by Users");
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body, DataRequest.class);
        RequestType requestType = request.getType();
        if (requestType == RequestType.BALANCE) {
            String accountNumber = request.getAccountNumber();
            //TODO fetch balance from database
            String balance = "125,50";
            DataReply reply = Util.createJsonReply(request.getAccountNumber(), requestType, balance);
            callback.reply(gson.toJson(reply));
        }
        else if (requestType == RequestType.TRANSACTIONHISTORY) {
            //TODO fetch transaction history
            String transactionHistory = "Dummy history";
            String accountNumber = request.getAccountNumber();
            DataReply reply = Util.createJsonReply(accountNumber, requestType, transactionHistory);
            callback.reply(gson.toJson(reply));
            System.out.println("Replied with dummy history");
        } else {
            callback.reject("Incorrect request type sent to ledger.");
        }
    }

    public void printLedger() {
        for (String key : this.ledger.keySet()) {
            System.out.printf("Account: %s Balance: %f", key, this.ledger.get(key));
        }
    }
}
