package api;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 */
@RequestMapping("/api")
final class ApiService {
    /** Connection to the ui service. */
    private HttpClient uiClient;
    /** Connection to the upini service. */
    private HttpClient pinClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Api Service. */
    private static final String PREFIX = "[API]                 :";
    /** Number of the ATM system for internal use*/
    private static final String ATMNUMBER = "NL52GNIB3676451168";

    /**
     * Constructor
     * @param uiPort Port the ui service is located on.
     * @param uiHost Host the ui service is located on.
     * @param pinPort Port the pin service is located on.
     * @param pinHost Host the pin service is located on.
     */
    public ApiService(final int uiPort, final String uiHost, final int pinPort, final String pinHost) {
        uiClient = httpClientBuilder().setHost(uiHost).setPort(uiPort).buildAndStart();
        pinClient = httpClientBuilder().setHost(pinHost).setPort(pinPort).buildAndStart();
        jsonConverter = new Gson();
    }

    /**
     * Checks the type of the request that was received and calls the according method handler.
     * @param callback Callback to the source of the request.
     * @param requestJson Json string containing the request that was made.
     */
    @RequestMapping(value = "/request", method = RequestMethod.POST)
    public void handleApiRequest(final Callback<String> callback, final String requestJson) {
        try {
            JSONRPC2Request request = JSONRPC2Request.parse(requestJson);
            String method = request.getMethod();
            Object id = request.getID();
            Map<String, Object> params = request.getNamedParams();
            CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
            switch (method) {
                case "openAccount":             openAccount(params, callbackBuilder, id);
                    break;
                case "openAdditionalAccount":   openAdditionalAccount(params, callbackBuilder, id);
                    break;
                case "closeAccount":            closeAccount(params, callbackBuilder, id);
                    break;
                case "provideAccess":           provideAccess(params, callbackBuilder, id);
                    break;
                case "revokeAccess":            revokeAccess(params, callbackBuilder, id);
                    break;
                case "depositIntoAccount":      depositIntoAccount(params, callbackBuilder, id);
                    break;
                case "payFromAccount":          payFromAccount(params, callbackBuilder, id);
                    break;
                case "transferMoney":           transferMoney(params, callbackBuilder, id);
                    break;
                case "getAuthToken":            getAuthToken(params, callbackBuilder, id);
                    break;
                case "getBalance":              getBalance(params, callbackBuilder, id);
                    break;
                case "getTransactionsOverview": getTransactionsOverview(params, callbackBuilder, id);
                    break;
                case "getUserAccess":           getUserAccess(params, callbackBuilder, id);
                    break;
                case "getBankAccountAccess":    getBankAccountAccess(params, callbackBuilder, id);
                    break;
                default:                        callback.reply(new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND,
                        request.getID()).toJSONString());
                    break;
            }
        } catch (JSONRPC2ParseException e) {
            callback.reply(new JSONRPC2Response(JSONRPC2Error.PARSE_ERROR).toJSONString());
        }
    }

    /**
     * Creates a customer object that represents the customer an account should be created for. This method will create
     * getAuthTokenForPinCard information for a customer, put the customer's information into the system and create a new bank account
     * for the customer.
     * @param params all parameters for the method call, if a parameter is not in this map the request will be rejected.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void openAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                             final Object id) {
        Customer customer = JSONParser.createJsonCustomer((String) params.get("initials"), (String) params.get("name"),
                (String) params.get("surname"), (String) params.get("email"), (String) params.get("telephoneNumber"),
                (String) params.get("address"), (String) params.get("dob"), Long.parseLong((String) params.get("ssn")),
                0.0, 0.0, 0L, (String) params.get("username"),
                (String) params.get("password"));
        doNewCustomerRequest(customer, callbackBuilder, id);
    }

    /**
     * Sends a new customer request to the ui service for processing, if this is successful logs in and requests a
     * new pin card and sends the result of the request back to the request source.
     * @param customer Customer that will be created in the system.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void doNewCustomerRequest(final Customer customer, final CallbackBuilder callbackBuilder, final Object id) {
        uiClient.putFormAsyncWith1Param("/services/ui/customer", "customer",
                jsonConverter.toJson(customer), (statusCode, contentType, replyJson) -> {
                    if (statusCode == HTTP_OK) {
                        Customer reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyJson),
                                                                Customer.class);
                        System.out.printf("%s Customer successfully created in the system.\n\n\n\n", PREFIX);
                        getAuthTokenForPinCard(customer.getUsername(), customer.getPassword(),
                                reply.getAccount().getAccountNumber(), callbackBuilder, id);
                    } else {
                        System.out.printf("%s Customer creation request failed, body: %s\n\n\n\n", PREFIX, replyJson);
                        //todo send error.
                    }
                });
    }

    /**
     * Performs a login request to fetch an authentication that can be used to request a new pin card, then performs a
     * new pin card request.
     * @param username Username to login with.
     * @param password Password to login with.
     * @param accountNumber AccountNumber the new pin card should be requested for.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getAuthTokenForPinCard(final String username, final String password, final String accountNumber,
                                        final CallbackBuilder callbackBuilder, final Object id) {
        Authentication authentication = JSONParser.createJsonAuthenticationLogin(username, password);
        System.out.printf("%s Logging in.\n", PREFIX);
        uiClient.putFormAsyncWith1Param("/services/ui/login", "authData",
                                        jsonConverter.toJson(authentication), (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Authentication authenticationReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                            Authentication.class);
                System.out.printf("%s Successful login, set the following cookie: %s\n\n\n\n",
                                    PREFIX, authenticationReply.getCookie());
                doNewPinCardRequest(accountNumber, username, authenticationReply.getCookie(), callbackBuilder, id,
                                    true);
            } else {
                System.out.printf("%s Login failed.\n\n\n\n", PREFIX);
                //todo send failed reply
            }
        });
    }

    /**
     * Performs a new pin card request for a given account number.
     * @param accountNumber AccountNumber the new pin card should be linked to.
     * @param username Username of the user the pinCard is for.
     * @param cookie Cookie used to perform the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     * @param accountNrInResult Boolean indicating if the accountNumber should be in the result of the request.
     */
    private void doNewPinCardRequest(final String accountNumber, final String username, final String cookie,
                                     final CallbackBuilder callbackBuilder, final Object id,
                                     final boolean accountNrInResult) {
        Gson gson = new Gson();
        uiClient.putFormAsyncWith3Params("/services/ui/card", "accountNumber", accountNumber,
                "cookie", cookie, "username", username, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        PinCard newPinCard = gson.fromJson(JSONParser.removeEscapeCharacters(body), PinCard.class);
                        System.out.printf("%s Successfully requested a new pin card.\n\n\n\n", PREFIX);
                        if (accountNrInResult) {
                            sendOpenAccountCallback(callbackBuilder, accountNumber, newPinCard.getCardNumber(),
                                    newPinCard.getPinCode(), id);
                        } else {
                            sendAccessRequestCallback(callbackBuilder, newPinCard.getCardNumber(),
                                    newPinCard.getPinCode(), id);
                        }
                    } else {
                        System.out.println(code);
                        System.out.println(body);
                        System.out.printf("%s New pin card request failed.\n\n\n\n", PREFIX);
                        //todo send failed reply
                    }
                });
    }

    /**
     * Creates and sends a JSONRPC response for an openAccount request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     * @param accountNumber AccountNumber of the opened account.
     * @param cardNumber CardNumber of the card created with the new account.
     * @param pinCode Pincode for the new pinCard.
     * @param id Id of the request.
     */
    private void sendOpenAccountCallback(final CallbackBuilder callbackBuilder, final String accountNumber,
                                         final Long cardNumber, final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("iBAN", accountNumber);
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /**
     * Creates and sends a JSONRPC response for an Access request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     * @param cardNumber CardNumber of the card created with the new access link.
     * @param pinCode Pincode for the new pinCard.
     * @param id Id of the request.
     */
    private void sendAccessRequestCallback(final CallbackBuilder callbackBuilder, final Long cardNumber,
                                           final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /**
     * Opens an additional account for a customer.
     * @param params Map containing all the request parameters(authToken).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void openAdditionalAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                       final Object id) {
        String cookie = (String) params.get("authToken");
        uiClient.putFormAsyncWith1Param("/services/ui/account/new", "cookie", cookie,
                                        (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Customer reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                        Customer.class);
                String accountNumber = reply.getAccount().getAccountNumber();
                System.out.printf("%s New Account creation successful, Account Holder: %s,"
                                    + " AccountNumber: %s\n\n\n\n", PREFIX, reply.getCustomerId(),
                                    accountNumber);
                doNewPinCardRequest(accountNumber, "", cookie, callbackBuilder, id, true);
            } else {
                System.out.printf("%s Account creation failed. body: %s\n\n\n\n", PREFIX, body);
            }
        });
    }

    /**
     * Removes an account from the system.
     * @param params Map containing the parameters of the request (authToken, IBAN).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void closeAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                              final Object id) {
        System.out.printf("%s Sending close account request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/account/remove", "accountNumber",
                params.get("iBAN"), "cookie", params.get("authToken"), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        sendCloseAccountCallback(callbackBuilder, id, body);
                    } else {
                        System.out.printf("%s Account closing failed.\n\n\n\n", PREFIX);
                        //todo send back result with appropriate error
                    }
                });
    }

    /**
     * Sends te result of the closeAccountRequest back to the request source using a JSONRPC object.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     * @param reply Used to show which accountNumber is closed.
     */
    private void sendCloseAccountCallback(final CallbackBuilder callbackBuilder, final Object id,
                                          final String reply) {
        AccountLink replyObject = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(reply), AccountLink.class);
        System.out.printf("%s Successfully closed account %s\n\n\n\n", PREFIX, replyObject.getAccountNumber());
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /**
     * Sends te result of the revokeAccess request back to the request source using a JSONRPC object.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void sendRevokeAccessCallback(final CallbackBuilder callbackBuilder, final Object id) {
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /**
     * Links an account to the user with the username specified in params. Then creates a new pin card for the user
     * and returns the pincard and pincode.
     * @param params Parameters of the request(authToken, iBAN, username).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void provideAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        // does an account Link to a username(so we need a conversion for this internally)
        // then performs a new pin card request for the customer with username.
        String accountNumber = (String) params.get("iBAN");
        String username = (String) params.get("username");
        String cookie = (String) params.get("authToken");
        AccountLink request = JSONParser.createJsonAccountLink(accountNumber, username, false);
        System.out.printf("%s Sending account link request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/accountLink", "request",
                jsonConverter.toJson(request), "cookie", cookie, (code, contentType, body) -> {
            if (code == HTTP_OK) {
                AccountLink reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), AccountLink.class);
                if (reply.isSuccessful()) {
                    System.out.printf("%s Account link successful for Account Holder: %s, AccountNumber: %s\n\n\n\n",
                            PREFIX, reply.getCustomerId(), accountNumber);
                    doNewPinCardRequest(accountNumber, username, cookie, callbackBuilder, id, false);
                } else {
                    System.out.printf("%s Account link creation unsuccessful.\n\n\n\n", PREFIX);
                    //todo send back failure
                }
            } else {
                System.out.printf("%s Account link creation failed.\n\n\n\n", PREFIX);
                //todo send back failure
            }
        });
    }

    /**
     * Removes a users access to an account based on the username specified.
     * @param params Parameters of the request (authToken, iBAN, username).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void revokeAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                              final Object id) {
        // performs an account Link removal and then removes the pincard(s) of said customer.
        // look at documentation for more specifics.
        String accountNumber = (String) params.get("iBAN");
        String username = (String) params.get("username");
        String cookie = (String) params.get("authToken");
        AccountLink request = JSONParser.createJsonAccountLink(accountNumber, username, false);
        System.out.printf("%s Sending account link removal request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/accountLink/remove", "request",
                jsonConverter.toJson(request), "cookie", cookie, (code, contentType, body) -> {
            if (code == HTTP_OK) {
                RemoveAccountLinkReply reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), RemoveAccountLinkReply.class);
                if (reply.isSuccessful()) {
                    System.out.printf("%s Account link removal successful for Account Holder: %s, AccountNumber: %s\n\n\n\n", PREFIX, reply.getMessage(), accountNumber);
                    sendRevokeAccessCallback(callbackBuilder, id);
                } else {
                    System.out.printf("%s Account link removal unsuccessful.\n\n\n\n", PREFIX);
                    //todo send back failure
                }
            } else {
                System.out.printf("%s Account link removal failed.\n\n\n\n", PREFIX);
                //todo send back failure
            }
        });
    }

    /**
     * Makes a deposit into an account using a pincard.
     * @param params Parameters of the request (iBAN, pinCard, pinCode, amount).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void depositIntoAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                    final Object id) {
        System.out.printf("%s Sending deposit transaction.\n", PREFIX);
        String accountNumber = (String) params.get("iBAN");
        String pinCode = (String) params.get("pinCode");
        String pinCard = (String) params.get("pinCard");
        Double amount = (Double) params.get("amount");
        PinTransaction pin = JSONParser.createJsonPinTransaction(ATMNUMBER, accountNumber,
                "", pinCode, Long.parseLong(pinCard), amount, true);
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "request",
                jsonConverter.toJson(pin), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.printf("%s ATM transaction successful.\n\n\n", PREFIX);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s ATM transaction couldn't be processed.\n\n\n", PREFIX);
                            //todo figure out how to fetch what cause the failed processing.
                        } else {
                            System.out.printf("%s ATM transaction was not successful.\n\n\n", PREFIX);
                            //todo send unsuccessful reply, find way to fetch cause of this.
                        }
                    } else {
                        System.out.printf("%s ATM transaction request failed.\n\n\n", PREFIX);
                        //todo figure out a way to find out what made the request fail.
                    }
                });
    }

    /**
     * A money transfer between accounts by use of a pinCard, the user doing the transaction needs to use a pinCard
     * linked to the sourceAccount.
     * @param params Parameters of the request (sourceIBAN, targetIBAN, pinCard, pinCode, amount).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void payFromAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                final Object id) {
        PinTransaction pin = JSONParser.createJsonPinTransaction((String) params.get("sourceIBAN"),
                (String) params.get("targetIBAN"), "", (String) params.get("pinCode"),
                Long.parseLong((String) params.get("pinCard")), (Double) params.get("amount"), false);
        System.out.printf("%s Sending pin transaction.\n", PREFIX);
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "request",
                jsonConverter.toJson(pin), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                   Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.printf("%s Pin transaction successful.\n\n\n", PREFIX);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Pin transaction couldn't be processed.\n\n\n", PREFIX);
                            //todo figure out how to fetch what cause the failed processing.
                        } else {
                            System.out.printf("%s Pin transaction was not successful.\n\n\n", PREFIX);
                            //todo send unsuccessful reply, find way to fetch cause of this.
                        }
                    } else {
                        System.out.printf("%s Pin transaction request failed.\n\n\n", PREFIX);
                        //todo figure out a way to find out what made the request fail.
                    }
                });
    }

    /**
     * Transfer money between accounts, the authToken needs to belong to a user that is authorized to make transactions
     * from the sourceAccount.
     * @param params Parameters of the request (authToken, sourceIBAN, targetIBAN, targetName, amount, description).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void transferMoney(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        String cookie = (String) params.get("authToken");
        Transaction transaction = JSONParser.createJsonTransaction(-1, (String) params.get("sourceIBAN"),
                (String) params.get("targetIBAN"), (String) params.get("targetName"),
                (String) params.get("description"), (Double) params.get("amount"), false, false);
        System.out.printf("%s Sending internal transaction.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/transaction", "request",
                jsonConverter.toJson(transaction), "cookie", cookie,
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                   Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            long transactionId = reply.getTransactionID();
                            System.out.printf("%s Internal transaction %d successful.\n\n\n\n",
                                    PREFIX, transactionId);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Internal transaction couldn't be processed\n\n\n\n", PREFIX);
                            //todo figure out how to fetch what cause the failed processing.
                        } else {
                            System.out.printf("%s Internal transaction was not successful\n\n\n\n", PREFIX);
                            Map<String, Object> result = new HashMap<>();
                            result.put("NotAuthorizedError", "User is not authorized to make this transaction.");
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                            //todo send unsuccessful reply, find way to fetch cause of this.
                        }
                    } else {
                        System.out.printf("%s Transaction request failed.\n\n\n\n", PREFIX);
                        //todo figure out a way to find out what made the request fail and add the proper error!!.
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(2,
                                                        "User is not authorized to make this transaction."), id);
                        callbackBuilder.build().reply(response.toJSONString());
                    }
                });
    }

    /**
     * Logs a user into the system and sends the user an authToken to authorize himself.
     * @param params Parameters of the request (username, password).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getAuthToken(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                              final Object id) {
        Authentication authentication = JSONParser.createJsonAuthenticationLogin((String) params.get("username"),
                (String) params.get("password"));
        Gson gson = new Gson();
        System.out.printf("%s Logging in.\n", PREFIX);
        uiClient.putFormAsyncWith1Param("/services/ui/login", "authData", gson.toJson(authentication),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Authentication authenticationReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                Authentication.class);
                        System.out.printf("%s Successful login, set the following cookie: %s\n\n\n\n",
                                PREFIX, authenticationReply.getCookie());
                        Map<String, Object> result = new HashMap<>();
                        result.put("authToken", authenticationReply.getCookie());
                        JSONRPC2Response response = new JSONRPC2Response(result, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s Login failed.\n\n\n\n", PREFIX);
                        System.out.println(body);
                        //todo return error.
                    }
                });
    }

    /**
     * Fetches the balance for a bank account, the authToken needs to belong to a user that is authorized to view
     * the balance of the iBAN.
     * @param params Parameters of the request (authToken, iBAN).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getBalance(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                            final Object id) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"), RequestType.BALANCE,
                                                                0L);
        System.out.printf("%s Sending getBalance request.\n", PREFIX);
        uiClient.getAsyncWith2Params("/services/ui/data", "request", jsonConverter.toJson(request),
                                    "cookie", params.get("authToken"), (code, contentType, body) -> {
            if (code == HTTP_OK) {
                DataReply balanceReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                DataReply.class);
                System.out.printf("%s Request successful, balance: %f\n\n\n\n", PREFIX,
                        balanceReply.getAccountData().getBalance());
                Map<String, Object> result = new HashMap<>();
                result.put("balance", balanceReply.getAccountData().getBalance());
                JSONRPC2Response response = new JSONRPC2Response(result, id);
                callbackBuilder.build().reply(response.toJSONString());
            } else {
                System.out.printf("%s Request not successful, body: %s\n", PREFIX, body);
                //todo return error.
            }
        });
    }

    /**
     * Fetches the transaction history of an account, the authToken needs to belong to a user that is authorized to view
     * the transaction history of the iBAN.
     * @param params Parameters of the request (authToken, iBAN, nrOfTransactions).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getTransactionsOverview(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                         final Object id) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                                                                RequestType.TRANSACTIONHISTORY, 0L);
        System.out.printf("%s Sending transactionOverview request.\n", PREFIX);
        uiClient.getAsyncWith2Params("/services/ui/data", "request", jsonConverter.toJson(request),
                "cookie", params.get("authToken"), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        DataReply balanceReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                        DataReply.class);
                        System.out.printf("%s TransactionOverview request successful.\n\n\n\n", PREFIX);
                        List<Map<String, Object>> transactionList = new ArrayList<>();
                        Long nrOfTransactions = (Long) params.get("nrOfTransactions");
                        List<Transaction> transactions = balanceReply.getTransactions().subList(0,
                                                                                Math.toIntExact(nrOfTransactions) - 1);
                        for (Transaction transaction : transactions) {
                            Map<String, Object> transactionMap = new HashMap<>();
                            transactionMap.put("sourceIBAN", transaction.getSourceAccountNumber());
                            transactionMap.put("targetIBAN", transaction.getDestinationAccountNumber());
                            transactionMap.put("targetName", transaction.getDestinationAccountHolderName());
                            transactionMap.put("date", transaction.getTimestamp());
                            //todo create proper date indication instead of timestamp.
                            transactionMap.put("amount", transaction.getTransactionAmount());
                            transactionMap.put("description", transaction.getDescription());
                            transactionList.add(transactionMap);
                        }
                        JSONRPC2Response response = new JSONRPC2Response(transactionList, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s Request not successful, body: %s\n", PREFIX, body);
                        //todo return error.
                    }
                });
    }

    /**
     * Fetches a list of all the accounts that a user has access to.
     * @param params Parameters of the request (authToken).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getUserAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        DataRequest request = JSONParser.createJsonDataRequest(null, RequestType.CUSTOMERACCESSLIST, 0L);
        System.out.printf("%s Sending UserAccess request.\n", PREFIX);
        uiClient.getAsyncWith2Params("/services/ui/data", "request", jsonConverter.toJson(request),
                "cookie", params.get("authToken"), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        DataReply accountsReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                         DataReply.class);
                        System.out.printf("%s Accounts request successful.\n\n\n\n", PREFIX);
                        List<Map<String, Object>> result = new ArrayList<>();
                        accountsReply.getAccounts().forEach(k -> {
                            Map<String, Object> account = new HashMap<>();
                            account.put("iBAN", k.getAccountNumber());
                            account.put("owner", k.getUsername());
                            result.add(account);
                        });
                        JSONRPC2Response response = new JSONRPC2Response(result, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s Accounts request not successful, body: %s\n", PREFIX, body);
                        //todo return error.
                    }
                });
    }

    /**
     * Fetches a list of all users that have access to a specific bankAccount.
     * @param params Parameters of the request (authToken, iBAN).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getBankAccountAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                      final Object id) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.ACCOUNTACCESSLIST, 0L);
        System.out.printf("%s Sending BankAccountAccess request.\n", PREFIX);
        uiClient.getAsyncWith2Params("/services/ui/data", "request", jsonConverter.toJson(request),
                "cookie", params.get("authToken"), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        DataReply accountsReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                DataReply.class);
                        System.out.printf("%s BankAccountAccess request successful.\n\n\n\n", PREFIX);
                        List<Map<String, Object>> result = new ArrayList<>();
                        accountsReply.getAccounts().forEach(k -> {
                            Map<String, Object> account = new HashMap<>();
                            account.put("username", k.getUsername());
                            result.add(account);
                        });
                        JSONRPC2Response response = new JSONRPC2Response(result, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s BankAccountAccess Request not successful, body: %s\n", PREFIX, body);
                        //todo return error.
                    }
                });
    }
}
