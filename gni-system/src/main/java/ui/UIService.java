package ui;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import databeans.Customer;
import databeans.DataReply;
import databeans.DataRequest;
import ledger.Transaction;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * Interface that outside users can use to view their balance, transaction history, customer information, create
 * new accounts and make transactions.
 */
@RequestMapping("/ui")
public final class UIService {
    /**Port that the users service can be found on.*/
    private int usersPort;
    /**Host that the users service can be found on.*/
    private String usersHost;

    /**
     * Constructor.
     * @param newUsersPort port the users service can be found on.
     * @param newUsersHost host the users service can be found on.
     */
    public UIService(final int newUsersPort, final String newUsersHost) {
        this.usersPort = newUsersPort;
        this.usersHost = newUsersHost;
    }

    /**
     * Process a data requests from a users.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param jsonRequest A Json String representing a DataRequest object {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, @RequestParam("body") final String jsonRequest) {
        HttpClient httpClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).build();
        httpClient.start();
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(jsonRequest, DataRequest.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doUserDataRequest(httpClient, request, gson, callbackBuilder);
    }

    /**
     * Forwards the data request to the User service and waits for a callback.
     * @param httpClient Httpclient to use to make an asynchronous request.
     * @param request DataRequest to forward to the User service.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doUserDataRequest(final HttpClient httpClient, final DataRequest request, final Gson gson,
                                             final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending data request to Users");
        httpClient.getAsyncWith1Param("/services/users/data", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        processUserDataReply(callbackBuilder, body, request, gson);
                    } else {
                        callbackBuilder.build().reject("Transaction history request failed.");
                    }
                });
    }

    /**
     * Checks if a data request was successfull and sends the reply back to the source of the request.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     * @param body Body of the callback, a Json string representing a DataReply object {@link DataReply}.
     * @param request Data request that was made to the User service.
     * @param gson Used for Json conversion.
     */
    private void processUserDataReply(final CallbackBuilder callbackBuilder, final String body,
                                                final DataRequest request, final Gson gson) {
        String replyJson = body.substring(1, body.length() - 1).replaceAll("\\\\", "");
        DataReply reply = gson.fromJson(replyJson, DataReply.class);
        if (reply.getAccountNumber().equals(request.getAccountNumber())
                && reply.getType() == request.getType()) {
            callbackBuilder.build().reply(gson.toJson(reply));
        } else {
            System.out.println("Transaction request failed on reply check.");
            callbackBuilder.build().reject("Transaction request failed.");
        }
    }

    /**
     * Sends an incoming transaction request to the User service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param body Body of the transaction request, a Json string representing a Transaction object {@link Transaction}
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, @RequestParam("body") final String body) {
        HttpClient httpClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).build();
        httpClient.start();
        Gson gson = new Gson();
        Transaction request = gson.fromJson(body, Transaction.class);
        System.out.printf("UI: Sending transaction to users\n\n");
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doTransactionRequest(httpClient, request, gson, callbackBuilder);
    }

    /**
     * Forwards transaction request to the User service and wait for a callback.
     * @param httpClient Client used to perform an asynchronous transaction request.
     * @param request Transaction request that should be processed.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final HttpClient httpClient, final Transaction request, final Gson gson,
                                      final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending transaction request to Users");
        httpClient.putFormAsyncWith1Param("/services/users/transaction", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        processTransactionReply(callbackBuilder, body, request, gson);
                    } else {
                        callbackBuilder.build().reject("Transaction request failed.");
                    }
                });
    }

    /**
     * Checks the result of a transaction request and sends the result to the source of the request.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     * @param body Body of the transaction reply, Json string representing a Transaction object {@link Transaction}.
     * @param request Transaction request that has been processed.
     * @param gson Used for Json conversion.
     */
    private void processTransactionReply(final CallbackBuilder callbackBuilder, final String body,
                                         final Transaction request, final Gson gson) {
        String replyJson = body.substring(1, body.length() - 1).replaceAll("\\\\", "");
        Transaction reply = gson.fromJson(replyJson, Transaction.class);
        request.setTransactionID(reply.getTransactionID());
        if (reply.equalsRequest(request)) {
            callbackBuilder.build().reply(gson.toJson(reply));
            System.out.println("Request successfull, transactionId: " + reply.getTransactionID());
        } else {
            System.out.println("Transaction request failed on reply check.");
            callbackBuilder.build().reject("Transaction request failed.");
        }
    }

    /**
     * Handles customer creation requests by forwarding the request to the users service and waiting for a callback.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param body Body of the request, a Json string representing a Customer object that should be
     *             created {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void createCustomer(final Callback<String> callback, @RequestParam("body") final String body) {
        HttpClient httpClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).build();
        httpClient.start();
        Gson gson = new Gson();
        System.out.println(body);
        Customer customer = gson.fromJson(body, Customer.class);
        System.out.println("after customer");
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doCustomerRequest(httpClient, customer, gson, callbackBuilder);
    }

    /**
     * Sends the customer request to the User service and then processes the reply accordingly.
     * @param httpClient HttpClient used to perform an asynchronous customer creation request.
     * @param customer Customer object that should be created in the system.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doCustomerRequest(final HttpClient httpClient, final Customer customer, final Gson gson,
                                   final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer creation request to Users");
        httpClient.putFormAsyncWith1Param("/services/users/customer", "body", gson.toJson(customer),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        processCustomerReply(callbackBuilder, body, customer, gson);
                    } else {
                        callbackBuilder.build().reject("Customer creation request failed.");
                    }
                });
    }

    /**
     * Handles the callback from the User service and sends the result of the request back to the source of the request.
     * @param callbackBuilder Used to send the result of the request to the source of the request.
     * @param body Json string representing a Customer object which is a reply to the creation request.
     * @param customer Customer object that was requested to be created.
     * @param gson Used for Json conversion.
     */
    private void processCustomerReply(final CallbackBuilder callbackBuilder, final String body,
                                      final Customer customer, final Gson gson) {
        Customer reply = gson.fromJson(body.substring(1, body.length() - 1)
                .replaceAll("\\\\", ""), Customer.class);
        if (reply.getAccount().getAccountNumber().length() > 1 && reply.getName()
                .equals(customer.getName()) && reply.getSurname()
                .equals(customer.getAccount().getAccountHolderName())) {
            System.out.println("successfull callback, sending back reply");
            callbackBuilder.build().reply(gson.toJson(reply));
        } else {
            System.out.println("Customer enrollment error, see data: " + reply);
            callbackBuilder.build().reject("Customer enrollment failed on reply.");
        }
    }
}


