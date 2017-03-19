package ui;

import com.google.gson.Gson;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;

import javax.xml.crypto.Data;
import java.util.ArrayList;
import java.util.List;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * Interface that outside users can use to view their balance, transaction history, customer information, create
 * new accounts and make transactions.
 */
@RequestMapping("/ui")
final class UIService {
    /** Connection to the users service.*/
    private HttpClient usersClient;

    /**
     * Constructor.
     * @param usersPort port the users service can be found on.
     * @param usersHost host the users service can be found on.
     */
    UIService(final int usersPort, final String usersHost) {
        usersClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).buildAndStart();
    }

    /**
     * Process a data requests from a users.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param jsonRequest A Json String representing a DataRequest object {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, @RequestParam("body") final String jsonRequest) {
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(jsonRequest, DataRequest.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doUserDataRequest(request, gson, callbackBuilder);
    }

    /**
     * Forwards the data request to the User service and waits for a callback.
     * @param request DataRequest to forward to the User service.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doUserDataRequest(final DataRequest request, final Gson gson,
                                             final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending data request to Users");
        usersClient.getAsyncWith1Param("/services/users/data", "body", gson.toJson(request),
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
        RequestType type = request.getType();
        if (type == RequestType.BALANCE || type == RequestType.TRANSACTIONHISTORY) {
            DataReply reply = gson.fromJson(replyJson, DataReply.class);
            if (reply.getAccountNumber().equals(request.getAccountNumber())) {
                System.out.println("UI: Sending callback.");
                callbackBuilder.build().reply(gson.toJson(reply));
            } else {
                System.out.println("UI: Request/reply account numbers do not match.");
                callbackBuilder.build().reject("Request/reply account numbers do not match.");
            }
        } else {
            if (type == RequestType.CUSTOMERDATA) {
                Customer reply = gson.fromJson(replyJson, Customer.class);
                System.out.println("UI: Sending callback.");
                callbackBuilder.build().reply(gson.toJson(reply));
            } else if (type == RequestType.ACCOUNTS) {
                DataReply reply = gson.fromJson(replyJson, DataReply.class);
                System.out.println("UI: Sending callback.");
                callbackBuilder.build().reply(gson.toJson(reply));
                //TODO FINISH
            }

        }
    }

    /**
     * Sends an incoming transaction request to the User service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param body Body of the transaction request, a Json string representing a Transaction object {@link Transaction}
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, @RequestParam("body") final String body) {
        Gson gson = new Gson();
        Transaction request = gson.fromJson(body, Transaction.class);
        System.out.printf("UI: Sending transaction to users\n");
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doTransactionRequest(request, gson, callbackBuilder);
    }

    /**
     * Forwards transaction request to the User service and wait for a callback.
     * @param request Transaction request that should be processed.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final Transaction request, final Gson gson,
                                      final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending transaction request to Users");
        usersClient.putFormAsyncWith1Param("/services/users/transaction", "body", gson.toJson(request),
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
            System.out.println("UI: Request successfull, transactionId: " + reply.getTransactionID());
        } else {
            System.out.println("UI: Transaction request failed on reply check.");
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
        Gson gson = new Gson();
        Customer customer = gson.fromJson(body, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doCustomerRequest(customer, gson, callbackBuilder);
    }

    /**
     * Sends the customer request to the User service and then processes the reply accordingly.
     * @param customer Customer object that should be created in the system.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doCustomerRequest(final Customer customer, final Gson gson,
                                   final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer creation request to Users");
        usersClient.putFormAsyncWith1Param("/services/users/customer", "body", gson.toJson(customer),
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
            System.out.println("UI: successfull callback, sending back reply");
            callbackBuilder.build().reply(gson.toJson(reply));
        } else {
            System.out.println("UI: Customer enrollment error, see data: " + reply);
            callbackBuilder.build().reject("Customer enrollment failed on reply.");
        }
    }

    /**
     * Handles customer account link requests by forwarding the request to the users service and waiting for a callback.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param body Body of the request, a Json string representing a Customer object that should be
     *             created {@link Customer}.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void createCustomerAccountLink(final Callback<String> callback, @RequestParam("body") final String body) {
        Gson gson = new Gson();
        AccountLink request = gson.fromJson(body, AccountLink.class);
        System.out.printf("UI: Received account link request for customer %d account number %s\n",
                            request.getCustomerId(), request.getAccountNumber());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        usersClient.putFormAsyncWith1Param("/services/users/account", "body", body,
                                            ((code, contentType, replyBody) -> {
            if (code == HTTP_OK) {
                String replyJson = replyBody.substring(1, replyBody.length() - 1).replaceAll("\\\\", "");
                AccountLink reply = gson.fromJson(replyJson, AccountLink.class);
                if (reply.isSuccessfull()) {
                    System.out.println("UI: Successfull account link, sending reply.");
                    callbackBuilder.build().reply(replyJson);
                } else {
                    System.out.println("UI: Account link unsuccessfull, sending reply.");
                    callbackBuilder.build().reply(replyJson);
                }
            } else {
                System.out.println("UI: Account link request failed, sending rejection.");
                callbackBuilder.build().reject("Request failed HTTP code: " + code);
            }
        }));
    }
}


