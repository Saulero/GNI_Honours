package users;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.Listen;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import queue.ServiceManager;
import util.*;

/**
 * @author Noel
 * @version 1
 * The User microservice, handles customer information.
 * Creates customer accounts.
 * Initiates transactions for customers
 */
@RequestMapping("/user")
public class UserService {
    /**
     * Listens on DATA_REQUEST_CHANNEL for services that request user data.
     * If the data is customer information loads this data from the database
     * and sends the information back in a dataReply object using
     * DATA_REPLY_CHANNEL.
     * @param dataRequest request objects containing the request type,
     *                    and the account number the request is for.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    private void processDataRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body, DataRequest.class);
        RequestType type = request.getType();
        if (type == RequestType.CUSTOMERDATA) {
            //TODO fetch customer information from database
            String customerInformation = "freekje";
            DataReply reply = Util.createJsonReply(request.getAccountNumber(), request.getType(), customerInformation);
            callback.reply(gson.toJson(reply));
        } else if (type == RequestType.BALANCE) {
            //TODO make ledger request
            DataReply ledgerReply = new DataReply(request.getAccountNumber(), type, "123,50");
        } else if (type == RequestType.TRANSACTIONHISTORY) {
            //TODO make ledger request
            DataReply ledgerReply = new DataReply(request.getAccountNumber(), type, "history 1234 bla");
        } else {
            System.out.println("Received a request of unknown type.");
        }
    }


    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    private void processTransactionRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);
        //TODO send transaction to transactionout
        System.out.println("Sent transaction to transactionOut");
        Transaction transacioninCallback = Util.createJsonTransaction(1234, "1234",
                                                        "1234","asdfg",
                                                        20.00, true,true);
        callback.reply(gson.toJson(transacioninCallback));
    }

    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    private void processNewCustomer(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Customer customer = gson.fromJson(body, Customer.class);
        //TODO enroll customer in database
        boolean enrolled = true;
        if (enrolled) {
            //TODO get customer information from database
            String customerName = customer.getName();
            String customerSurname = customer.getSurname();
            String customerAccountNumber = customer.getAccountNumber();
            Customer enrolledCustomer = Util.createJsonCustomer(customerName, customerSurname, customerAccountNumber,
                    enrolled);
            callback.reply(gson.toJson(enrolledCustomer));
        }
    }
}
