package ui;

import com.google.gson.Gson;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 2
 * Interface that outside users can use to view their balance, transaction history, customer information, create
 * new accounts and make transactions.
 */
@RequestMapping("/ui")
final class UIService {
    /** Connection to the users service. */
    private HttpClient usersClient;
    /** Used for json conversions. */
    private Gson jsonConverter;

    /**
     * Constructor.
     * @param usersPort port the users service can be found on.
     * @param usersHost host the users service can be found on.
     */
    UIService(final int usersPort, final String usersHost) {
        usersClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).buildAndStart();
        jsonConverter = new Gson();
    }

    /**
     * Process a data requests from a users.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param dataRequestJson A Json String representing a DataRequest object {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   @RequestParam("body") final String dataRequestJson) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doDataRequest(dataRequestJson, callbackBuilder);
    }

    /**
     * Forwards the data request to the User service and waits for a callback.
     * @param dataRequest DataRequest to forward to the User service.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doDataRequest(final String dataRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending data request to UsersService.");
        usersClient.getAsyncWith1Param("/services/users/data", "body",
                                        dataRequestJson, (httpStatusCode, httpContentType, dataReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        processDataReply(dataReplyJson, dataRequestJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Transaction history request failed.");
                    }
                });
    }

    /**
     * Checks if a data request was successfull and sends the reply back to the source of the request.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     * @param dataReplyJson Body of the callback, a Json string representing a DataReply object {@link DataReply}.
     * @param dataRequest Data request that was made to the User service.
     * @param gson Used for Json conversion.
     */
    private void processDataReply(final String dataReplyJson, final String dataRequestJson,
                                  final CallbackBuilder callbackBuilder) {
        DataRequest dataRequest = jsonConverter.fromJson(dataRequestJson, DataRequest.class);
        RequestType requestType = dataRequest.getType();
        switch (requestType) {
            case BALANCE:
                sendBalanceRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case TRANSACTIONHISTORY:
                sendTransactionHistoryRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case CUSTOMERDATA:
                sendCustomerDataRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case ACCOUNTS:
                sendAccountsRequestCallback(dataReplyJson, callbackBuilder);
                break;
            default:
                callbackBuilder.build().reject("Incorrect requestType specified.");
                break;
        }
    }

    private void sendBalanceRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending balance request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    private void sendTransactionHistoryRequestCallback(final String dataReplyJson,
                                                       final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending transaction history request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    private void sendCustomerDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer data request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    private void sendAccountsRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending accounts request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    /**
     * Sends an incoming transaction request to the User service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param transactionRequestJson Body of the transaction request, a Json string representing a Transaction object {@link Transaction}
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("body") final String transactionRequestJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doTransactionRequest(JSONParser.sanitizeJson(transactionRequestJson), callbackBuilder);
    }

    /**
     * Forwards transaction request to the User service and wait for a callback.
     * @param transactionRequestJson Transaction request that should be processed.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final String transactionRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Forwarding transaction request.");
        usersClient.putFormAsyncWith1Param("/services/users/transaction", "body",
                                            transactionRequestJson,
                                            (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendTransactionRequestCallback(transactionReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Transaction request failed.");
                    }
                });
    }

    private void sendTransactionRequestCallback(final String transactionReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Transaction successfully executed, sent callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(transactionReplyJson));
    }

    /**
     * Handles customer creation requests by forwarding the request to the users service and waiting for a callback.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newCustomerRequestJson Body of the request, a Json string representing a Customer object that should be
     *             created {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    //todo rewrite so we can use initials + surname for accountholdername, where does account currently come from?
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("body") final String newCustomerRequestJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doNewCustomerRequest(newCustomerRequestJson, callbackBuilder);
    }

    /**
     * Sends the customer request to the User service and then processes the reply accordingly.
     * @param customer Customer object that should be created in the system.
     * @param gson Used for Json conversion.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doNewCustomerRequest(final String newCustomerRequestJson,
                                      final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer creation request to Users");
        usersClient.putFormAsyncWith1Param("/services/users/customer", "body",
                                            newCustomerRequestJson,
                                            (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendNewCustomerRequestCallback(newCustomerReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Customer creation request failed.");
                    }
                });
    }

    private void sendNewCustomerRequestCallback(final String newCustomerReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Customer creation successfull, sending callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(newCustomerReplyJson));
    }

    /**
     * Handles customer account link requests by forwarding the request to the users service and waiting for a callback.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkRequestJson Body of the request, a Json string representing an AccountLink object that should be
     *             created {@link AccountLink}.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback,
                                          @RequestParam("body") final String accountLinkRequestJson) {
        AccountLink accountLinkRequest = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        System.out.printf("UI: Received account link request for customer %d account number %s\n",
                          accountLinkRequest.getCustomerId(), accountLinkRequest.getAccountNumber());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountLinkRequest(accountLinkRequestJson, callbackBuilder);
    }

    private void doAccountLinkRequest(final String accountLinkRequestJson, final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/account", "body", accountLinkRequestJson,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendAccountLinkRequestCallback(accountLinkReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("AccountLink request failed.");
                    }
                }));
    }

    private void sendAccountLinkRequestCallback(final String accountLinkReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Successfull account link, sending callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(accountLinkReplyJson));
    }

    /**
     * Handles the creation of a new account for an existing customer by forwarding this request to the users service
     * and waiting for a callback.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newAccountRequestJson Body of the request, a Json string representing an AccountLink object with a customer id of the
     *             customer that an account needs to be created for.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("body") final String newAccountRequestJson) {
        Customer accountOwner = jsonConverter.fromJson(newAccountRequestJson, Customer.class);
        System.out.printf("UI: Received account creation request for customer %d\n", accountOwner.getId());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder()
                                                                   .withStringCallback(callback);
        doNewAccountRequest(newAccountRequestJson, callbackBuilder);
    }

    private void doNewAccountRequest(final String newAccountRequestJson,
                                     final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/account/new", "body", newAccountRequestJson,
                (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendNewAccountRequestCallback(newAccountReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("NewAccount request failed.");
                    }
                });
    }

    private void sendNewAccountRequestCallback(final String newAccountReplyJson,
                                               final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Successfull account creation request, sending callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(newAccountReplyJson));
    }
}


