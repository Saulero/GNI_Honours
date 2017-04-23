package ui;

import com.google.gson.Gson;
import database.ConnectionPool;
import databeans.*;
import io.advantageous.qbit.annotation.*;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import javax.xml.ws.RequestWrapper;
import java.security.SecureRandom;

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
    /** Connection to the authentication service. */
    private HttpClient authenticationClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the UI Service. */
    private static final String prefix = "[UI]                  :";

    /**
     * Constructor.
     * @param authenticationPort port the authentication service can be found on.
     * @param authenticationHost host the authentication service can be found on.
     */
    UIService(final int authenticationPort, final String authenticationHost) {
        authenticationClient = httpClientBuilder().setHost(authenticationHost).setPort(authenticationPort)
                                                                              .buildAndStart();
        jsonConverter = new Gson();
    }

    /**
     * Creates a callback builder for the data request and then forwards the request to the UsersService.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param dataRequestJson A Json String representing a DataRequest object {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   @RequestParam("request") final String dataRequestJson,
                                   @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doDataRequest(dataRequestJson, cookie, callbackBuilder);
    }

    /**
     * Forwards the data request to the Users service and sends the reply off to processing, or rejects the request if
     * the forward fails.
     * @param dataRequestJson Json string representing a dataRequest that should be sent to the UsersService.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doDataRequest(final String dataRequestJson, final String cookie,
                               final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding data request.\n", prefix);
        authenticationClient.getAsyncWith2Params("/services/authentication/data", "request",
                                                  dataRequestJson, "cookie", cookie,
                                                  (httpStatusCode, httpContentType, dataReplyJson) -> {
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
        System.out.printf("%s Sending balance request callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a transaction history request to the service that requested it.
     * @param dataReplyJson Json String containing the reply data {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionHistoryRequestCallback(final String dataReplyJson,
                                                       final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending transaction history request callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a customer data request to the service that requested it.
     * @param dataReplyJson Json String containing a customer {@link Customer}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendCustomerDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending customer data request callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of an accounts request to the service that requested it.
     * @param dataReplyJson Json String containing a data reply with the accounts belonging to a certain customer
     *                      {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendAccountsRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending accounts request callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
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
                                          @RequestParam("request") final String transactionRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doTransactionRequest(JSONParser.removeEscapeCharacters(transactionRequestJson), cookie, callbackBuilder);
    }

    /**
     * Forwards transaction request to the User service and forwards the reply or sends a rejection if the request
     * fails.
     * @param transactionRequestJson Transaction request that should be processed.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final String transactionRequestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding transaction request.\n", prefix);
        authenticationClient.putFormAsyncWith2Params("/services/authentication/transaction", "request",
                                            transactionRequestJson, "cookie", cookie,
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
        System.out.printf("%s Transaction successfully executed, sending callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(transactionReplyJson));
    }

    /**
     * Handles customer creation requests by forwarding the request to the users service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newCustomerRequestJson Json String representing a Customer that should be created {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("customer") final String newCustomerRequestJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doNewCustomerRequest(newCustomerRequestJson, callbackBuilder);
    }

    /**
     * Sends the customer request to the User service and then processes the reply, or sends a rejection to the
     * requester if the request fails..
     * @param newCustomerRequestJson Json String representing a Customer that should be created {@link Customer}.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doNewCustomerRequest(final String newCustomerRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding customer creation request.\n", prefix);
        //System.out.println(newCustomerRequestJson);
        authenticationClient.putFormAsyncWith1Param("/services/authentication/customer", "customer",
                                            newCustomerRequestJson,
                                            (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        //System.out.println("sending callback");
                        sendNewCustomerRequestCallback(newCustomerReplyJson, callbackBuilder);
                    } else {
                        //System.out.println("fail: " + newCustomerReplyJson);
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
        System.out.printf("%s Customer creation successfull, sending callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newCustomerReplyJson));
    }

    /**
     * Creates a callback builder for the account link request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkRequestJson Json string representing an AccountLink that should be created in the
     *                               database {@link AccountLink}.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback,
                                          @RequestParam("request") final String accountLinkRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        AccountLink accountLinkRequest = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        System.out.printf("%s Forwarding account link request for customer %d account number %s.\n",
                          prefix, accountLinkRequest.getCustomerId(), accountLinkRequest.getAccountNumber());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountLinkRequest(accountLinkRequestJson, cookie, callbackBuilder);
    }

    /**
     * Forwards a String representing an account link to the Users database, and processes the reply if it is successfull
     * or sends a rejection to the requesting service if it fails.
     * @param accountLinkRequestJson String representing an account link that should be executed {@link AccountLink}.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRequest(final String accountLinkRequestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/account", "request",
                accountLinkRequestJson, "cookie", cookie,
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
        System.out.printf("%s Successfull account link, sending callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(accountLinkReplyJson));
    }

    /**
     * Creates a callback builder for the account creation request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("request") final String newAccountRequestJson,
                                         @RequestParam("cookie") final String cookie) {
        Customer accountOwner = jsonConverter.fromJson(newAccountRequestJson, Customer.class);
        System.out.printf("%s Forwarding account creation request for customer %d.\n", prefix, accountOwner.getId());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder()
                                                                   .withStringCallback(callback);
        doNewAccountRequest(newAccountRequestJson, cookie, callbackBuilder);
    }

    /**
     * Forwards the Json String representing a customer with the account to be created to the Users Service and sends
     * the result back to the requesting service, or rejects the request if the forwarding fails.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doNewAccountRequest(final String newAccountRequestJson, final String cookie,
                                     final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/account/new", "request",
                newAccountRequestJson, "cookie", cookie,
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
        System.out.printf("%s Successfull account creation request, sending callback.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newAccountReplyJson));
    }

    @RequestMapping(value = "/login", method = RequestMethod.PUT)
    public void processLoginRequest(final Callback<String> callback,
                                    @RequestParam("authData") final String authDataJson) {
        System.out.printf("%s Forwarding login request.\n", prefix);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doLoginRequest(authDataJson, callbackBuilder);
    }

    private void doLoginRequest(final String authDataJson, final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith1Param("/services/authentication/login", "authData",
                authDataJson, (code, contentType, body) -> {
            if (code == HTTP_OK) {
                sendLoginRequestCallback(body, callbackBuilder);
            } else {
                //System.out.println(body);
                callbackBuilder.build().reject("Login not successfull.");
            }
                });
    }

    private void sendLoginRequestCallback(final String loginReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Login successfull, sending callback containing cookie.\n", prefix);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(loginReplyJson));
    }
}


