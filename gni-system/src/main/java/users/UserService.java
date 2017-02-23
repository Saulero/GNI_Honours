package users;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.Customer;
import util.DataReply;
import util.DataRequest;
import util.RequestType;
import util.Transaction;
import util.Util;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * @author Noel
 * @version 1
 * The User microservice, handles customer information and is used as a gateway for the UI service.
 */
@RequestMapping("/user")
public class UserService {
    /**Used to verify if a http request to another service was successfull.*/
    private static final int HTTP_OK = 200;
    /**Port that the Ledger service can be found on.*/
    private int ledgerPort;
    /**Host that the User service can be found on.*/
    private String ledgerHost;
    /**Port that the TransactionDispatch service can be found on.*/
    private int transactionDispatchPort;
    /**Host that the TransactionDispatch service can be found on.*/
    private String transactionDispatchHost;

    /**
     * Constructor.
     * @param newLedgerPort Port the Ledger service can be found on.
     * @param newLedgerHost Host the Ledger service can be found on.
     * @param newTransactionDispatchPort Port the TransactionDispatch service can be found on.
     * @param newTransactionDispatchHost Host the TransactionDispatch service can be found on.
     */
    UserService(final int newLedgerPort, final String newLedgerHost, final int newTransactionDispatchPort,
                       final String newTransactionDispatchHost) {
        this.ledgerPort = newLedgerPort;
        this.ledgerHost = newLedgerHost;
        this.transactionDispatchPort = newTransactionDispatchPort;
        this.transactionDispatchHost = newTransactionDispatchHost;
    }

    /**
     * Processes incoming data requests from the UI service and sends a reply back through a callback, if necessary
     * sends the request to the Ledger service and waits for a callback from the Ledger.
     * @param callback Used to send result back to the UI service.
     * @param body Json String representing a DataRequest that is made by the UI service {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        System.out.println("Users: Called by UI, calling Ledger");
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body, DataRequest.class);
        RequestType type = request.getType();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        if (request.getType() == RequestType.CUSTOMERDATA) {
            //TODO fetch customer information from database
            String customerInformation = "freekje";
            DataReply reply = Util.createJsonReply(request.getAccountNumber(), request.getType(), customerInformation);
            callbackBuilder.build().reply(gson.toJson(reply));
        } else {
            doDataRequest(request, gson, callbackBuilder);
        }
    }

    /**
     * Sends a data request to the Ledger and handles the response from the ledger. Uses the callbackBuilder to send
     * the reply from the ledger back to the UI service.
     * @param request DataRequest that was sent to the ledger {@link DataRequest}.
     * @param gson Used for Json conversions.
     * @param callbackBuilder Used to send the reply of the ledger back to the UI service.
     */
    private void doDataRequest(final DataRequest request, final Gson gson, final CallbackBuilder callbackBuilder) {
        HttpClient httpClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).build();
        httpClient.start();
        if (request.getType() == RequestType.BALANCE || request.getType() == RequestType.TRANSACTIONHISTORY) {
            httpClient.getAsyncWith1Param("/services/ledger/data", "body", gson.toJson(request),
                    (code, contentType, replyBody) -> {
                if (code == HTTP_OK) {
                    DataReply reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                                    .replaceAll("\\\\", ""), DataReply.class);
                    callbackBuilder.build().reply(gson.toJson(reply));
                } else {
                    callbackBuilder.build().reject("Recieved an error from ledger.");
                }
            });
        } else {
            callbackBuilder.build().reject("Received a request of unknown type.");
        }
    }

    /**
     * Processes transaction requests coming from the UI service by forwarding them to the TransactionDispatch service.
     * @param callback Used to send the result back to the UI service.
     * @param body Json String containing a Transaction object for a transaction request.
     */
    //TODO handle reply from TransactionDispatch service
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);
        //TODO send transaction to TransactionDispatch service
        System.out.println("Sent transaction to TransactionDispatch");
        Transaction transactionInReply = transaction;
        callback.reply(gson.toJson(transactionInReply));
    }

    /**
     * Processes customer creation requests coming from the UI service, sends the request to the Ledger service to
     * obtain an accountNumber for the customer and then processes the customer in the User database.
     * @param callback Used to send the result of the request back to the UI service.
     * @param body Json string containing the Customer object the request is for {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomer(final Callback<String> callback, final @RequestParam("body") String body) {
        HttpClient httpClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).build();
        httpClient.start();
        Gson gson = new Gson();
        Customer customer = gson.fromJson(body, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doCustomerRequest(httpClient, gson, customer, callbackBuilder);
    }

    /**
     * Sends request for obtaining an accountNumber to the ledger, then processes the customer request internally in
     * the User database and sends a reply back to the UI service.
     * @param httpClient HttpClient used to send an asynchronous accountNumber request to the ledger.
     * @param gson Used to do Json conversions.
     * @param customer Customer object that was used to make a new customer request.
     * @param callbackBuilder Used to send the result of the customer request back to the UI service.
     */
    private void doCustomerRequest(final HttpClient httpClient, final Gson gson, final Customer customer,
                                   final CallbackBuilder callbackBuilder) {
        httpClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body", gson.toJson(customer),
                (code, contentType, replyBody) -> {
                    if (code == HTTP_OK) {
                        Customer ledgerReply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                .replaceAll("\\\\", ""), Customer.class);
                        String accountNumber = ledgerReply.getAccountNumber();
                        boolean enrolled = ledgerReply.getEnrolled();
                        if (enrolled) {
                            String customerName = customer.getName();
                            String customerSurname = customer.getSurname();
                            Customer enrolledCustomer = Util.createJsonCustomer(customerName, customerSurname,
                                    accountNumber, enrolled);
                            //TODO enroll customer in database
                            callbackBuilder.build().reply(gson.toJson(enrolledCustomer));
                        } else {
                            callbackBuilder.build().reject("Ledger failed to enroll.");
                        }
                    } else {
                        callbackBuilder.build().reject("Recieved an error from ledger.");
                    }
                });
    }
}
