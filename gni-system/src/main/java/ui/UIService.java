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
     * Creates a callback builder for the data request and then forwards the request to the UsersService.
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
     * Forwards the data request to the Users service and sends the reply off to processing, or rejects the request if
     * the forward fails.
     * @param dataRequestJson Json string representing a dataRequest that should be sent to the UsersService.
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
     * @param dataReplyJson Body of the callback, a Json string representing a DataReply object {@link DataReply}.
     * @param dataRequestJson Json string containing the dataRequest that was forwarded {@link DataRequest}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
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

    /**
     * Forwards the result of a balance request to the service that requested it.
     * @param dataReplyJson Json String containing the reply data {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendBalanceRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending balance request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    /**
     * Forwards the result of a transaction history request to the service that requested it.
     * @param dataReplyJson Json String containing the reply data {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionHistoryRequestCallback(final String dataReplyJson,
                                                       final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending transaction history request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    /**
     * Forwards the result of a customer data request to the service that requested it.
     * @param dataReplyJson Json String containing a customer {@link Customer}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendCustomerDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer data request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    /**
     * Forwards the result of an accounts request to the service that requested it.
     * @param dataReplyJson Json String containing a data reply with the accounts belonging to a certain customer
     *                      {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendAccountsRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending accounts request callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(dataReplyJson));
    }

    /**
     * Creates a callback builder to forward the result of the request to the requester, and then forwards the request
     * to the Users service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param transactionRequestJson Json String representing a Transaction object that is to be processed
     *                               {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("body") final String transactionRequestJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doTransactionRequest(JSONParser.sanitizeJson(transactionRequestJson), callbackBuilder);
    }

    /**
     * Forwards transaction request to the User service and forwards the reply or sends a rejection if the request
     * fails.
     * @param transactionRequestJson Transaction request that should be processed.
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

    /**
     * Forwards the result of a transaction request to the service that sent the request.
     * @param transactionReplyJson Json String representing the executed transaction {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionRequestCallback(final String transactionReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Transaction successfully executed, sent callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(transactionReplyJson));
    }

    /**
     * Handles customer creation requests by forwarding the request to the users service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newCustomerRequestJson Json String representing a Customer that should be created {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("body") final String newCustomerRequestJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doNewCustomerRequest(newCustomerRequestJson, callbackBuilder);
    }

    /**
     * Sends the customer request to the User service and then processes the reply, or sends a rejection to the
     * requester if the request fails..
     * @param newCustomerRequestJson Json String representing a Customer that should be created {@link Customer}.
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

    /**
     * Forwards the created customer back to the service that sent the customer creation request to this service.
     * @param newCustomerReplyJson Json String representing a customer that was created in the system.
     * @param callbackBuilder Json String representing a Customer that should be created {@link Customer}.
     */
    private void sendNewCustomerRequestCallback(final String newCustomerReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Customer creation successfull, sending callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(newCustomerReplyJson));
    }

    /**
     * Creates a callback builder for the account link request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkRequestJson Json string representing an AccountLink that should be created in the
     *                               database {@link AccountLink}.
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

    /**
     * Forwards a String representing an account link to the Users database, and processes the reply if it is successfull
     * or sends a rejection to the requesting service if it fails.
     * @param accountLinkRequestJson String representing an account link that should be executed {@link AccountLink}.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
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

    /**
     * Forwards the result of an account link request to the service that sent the request.
     * @param accountLinkReplyJson Json String representing the result of an account link request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendAccountLinkRequestCallback(final String accountLinkReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Successfull account link, sending callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(accountLinkReplyJson));
    }

    /**
     * Creates a callback builder for the account creation request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
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

    /**
     * Forwards the Json String representing a customer with the account to be created to the Users Service and sends
     * the result back to the requesting service, or rejects the request if the forwarding fails.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
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

    /**
     * Sends the result of an account creation request to the service that requested it.
     * @param newAccountReplyJson Json String representing a customer with a linked account that was newly created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendNewAccountRequestCallback(final String newAccountReplyJson,
                                               final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Successfull account creation request, sending callback.");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(newAccountReplyJson));
    }
}


