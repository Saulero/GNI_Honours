package ui;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import jdk.nashorn.internal.codegen.CompilerConstants;
import util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 5-2-17.
 * Interface that outside users can use to view their balance, transaction
 * history, and make transactions.
 */
@RequestMapping("/ui")
public final class UIService {

    /**
     * Send a transaction request to the ledger service that will reply with
     * the transaction history of the account.
     * @param accountNumber Account number to request the transaction history
     *                      for.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, @RequestParam("body") final String jsonRequest) {
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(jsonRequest, DataRequest.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doUserDataRequest(httpClient, request, gson, callbackBuilder);
    }

    private void doUserDataRequest(final HttpClient httpClient, final DataRequest request, final Gson gson,
                                             final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending data request to Users");
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == 200) {
                        processUserDataReply(callbackBuilder, body, request, gson);
                    } else {
                        callbackBuilder.build().reject("Transaction history request failed.");
                    }
                });
    }

    private void processUserDataReply(final CallbackBuilder callbackBuilder, final String body,
                                                final DataRequest request, final Gson gson) {
        String replyJson = body.substring(1, body.length() - 1).replaceAll("\\\\", "");
        DataReply reply = gson.fromJson(replyJson, DataReply.class);
        if (reply.getAccountNumber().equals(request.getAccountNumber())
                && reply.getType() == request.getType()) {
            callbackBuilder.build().reply(gson.toJson(reply));
            System.out.println("Request successfull, data: " +  reply.getData());
        } else {
            System.out.println("Transaction request failed on reply check.");
            callbackBuilder.build().reject("Transaction request failed.");
        }
    }

    /**
     * Send a transaction request to the transaction out service.
     * @param sourceAccountNumber Account number to draw the funds from
     * @param amount Amount of money to send
     * @param destinationAccountNumber Account Number to send the money to
     * @param destinationAccountHolderName The name of the owner of the
     *                                     destination account number
     * @param transactionNumber Transaction number used for processing
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, @RequestParam("body") final String body) {
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        Gson gson = new Gson();
        Transaction request = gson.fromJson(body, Transaction.class);
        System.out.printf("UI: Sending transaction to users\n\n");
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doTransactionRequest(httpClient, request, gson, callbackBuilder);
    }

    private void doTransactionRequest(final HttpClient httpClient, final Transaction request, final Gson gson,
                                      final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending transaction request to Users");
        httpClient.putFormAsyncWith1Param("/services/user/transaction", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == 200) {
                        processTransactionReply(callbackBuilder, body, request, gson);
                    } else {
                        callbackBuilder.build().reject("Transaction request failed.");
                    }
                });
    }

    private void processTransactionReply(final CallbackBuilder callbackBuilder, final String body,
                                         final Transaction request, final Gson gson) {
        String replyJson = body.substring(1, body.length() - 1).replaceAll("\\\\", "");
        Transaction reply = gson.fromJson(replyJson, Transaction.class);
        request.setTransactionID(reply.getTransactionID());
        if (reply.equals(request)) {
            callbackBuilder.build().reply(gson.toJson(reply));
            System.out.println("Request successfull, transactionId: " + reply.getTransactionID());
        } else {
            System.out.println("Transaction request failed on reply check.");
            callbackBuilder.build().reject("Transaction request failed.");
        }
    }

    /**
     * Send a user creation request over the USER_CREATION_CHANNEL to create
     * a new user in the system.
     * @param name First name of the user to create
     * @param surname Surname of the user to create
     * @param accountNumber Account number of the user to create
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void createCustomer(final Callback<String> callback, @RequestParam("body") final String body) {
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        Gson gson = new Gson();
        System.out.println(body);
        Customer customer = gson.fromJson(body, Customer.class);
        System.out.println("after customer");
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doCustomerRequest(httpClient, customer, gson, callbackBuilder);
    }

    private void doCustomerRequest(final HttpClient httpClient, final Customer customer, final Gson gson,
                                   final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer creation request to Users");
        httpClient.putFormAsyncWith1Param("/services/user/customer", "body", gson.toJson(customer),
                (code, contentType, body) -> {
                    if (code == 200) {
                        processCustomerReply(callbackBuilder, body, customer, gson);
                    } else {
                        callbackBuilder.build().reject("Customer creation request failed.");
                    }
                });
    }

    private void processCustomerReply(final CallbackBuilder callbackBuilder, final String body,
                                      final Customer customer, final Gson gson) {
        Customer reply = gson.fromJson(body.substring(1, body.length() - 1)
                .replaceAll("\\\\", ""), Customer.class);
        if (reply.getEnrolled() && reply.getAccountNumber().length() > 1 && reply.getName()
                .equals(customer.getName()) && reply.getSurname().equals(customer.getSurname())) {
            System.out.println("successfull callback, sending back reply");
            callbackBuilder.build().reply(gson.toJson(reply));
        } else {
            System.out.println("Customer enrollment error, see data: " + reply);
            callbackBuilder.build().reject("Customer enrollment failed on reply.");
        }
    }
}


