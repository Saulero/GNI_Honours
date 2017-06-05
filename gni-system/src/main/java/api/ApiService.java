package api;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
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
public final class ApiService {
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

    public ApiService(final int uiPort, final String uiHost, final int pinPort, final String pinHost) {
        uiClient = httpClientBuilder().setHost(uiHost).setPort(uiPort).buildAndStart();
        pinClient = httpClientBuilder().setHost(pinHost).setPort(pinPort).buildAndStart();
        jsonConverter = new Gson();
    }

    @RequestMapping(value = "/request", method = RequestMethod.POST)
    public void handleApiRequest(final Callback<String> callback, final @RequestParam("request") String requestJson) {
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

    private void openAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                             final Object id) {
        Customer customer = JSONParser.createJsonCustomer((String) params.get("initials"), (String) params.get("name"),
                (String) params.get("surname"), (String) params.get("email"), (String) params.get("telephoneNumber"),
                (String) params.get("address"), (String) params.get("dob"), (Long) params.get("ssn"),
                0.0, 0.0, 0L, (String) params.get("username"),
                (String) params.get("password"));
        doNewCustomerRequest(customer, callbackBuilder, id);
    }

    private void doNewCustomerRequest(final Customer customer, final CallbackBuilder callbackBuilder, final Object id) {
        uiClient.putFormAsyncWith1Param("/services/ui/customer", "customer",
                jsonConverter.toJson(customer), (statusCode, contentType, replyJson) -> {
                    if (statusCode == HTTP_OK) {
                        Customer reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyJson), Customer.class);
                        System.out.printf("%s Customer successfully created in the system.\n\n\n\n", PREFIX);
                        login(customer.getUsername(), customer.getPassword(),
                                reply.getAccount().getAccountNumber(), callbackBuilder, id);
                    } else {
                        System.out.printf("%s Customer creation request failed, body: %s\n\n\n\n", PREFIX, replyJson);
                        //todo send error.
                    }
                });
    }

    private void login(final String username, final String password, final String accountNumber,
                       final CallbackBuilder callbackBuilder, final Object id) {
        Authentication authentication = JSONParser.createJsonAuthenticationLogin(username, password);
        Gson gson = new Gson();
        System.out.printf("%s Logging in.\n", PREFIX);
        uiClient.putFormAsyncWith1Param("/services/ui/login", "authData", gson.toJson(authentication),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Authentication authenticationReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                Authentication.class);
                        System.out.printf("%s Successfull login, set the following cookie: %s\n\n\n\n",
                                PREFIX, authenticationReply.getCookie());
                        doNewPinCardRequest(accountNumber, authenticationReply.getCookie(), callbackBuilder, id, false);
                    } else {
                        System.out.printf("%s Login failed.\n\n\n\n", PREFIX);
                        //todo send failed reply
                    }
                });
    }

    private void doNewPinCardRequest(final String accountNumber, final String cookie,
                                     final CallbackBuilder callbackBuilder, final Object id,
                                     final boolean accountNrInResult) {
        Gson gson = new Gson();
        uiClient.putFormAsyncWith2Params("/services/ui/card", "accountNumber", accountNumber,
                "cookie", cookie, (code, contentType, body) -> {
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
                        System.out.printf("%s New pin card request failed.\n\n\n\n", PREFIX);
                        //todo send failed reply
                    }
                });
    }

    private void sendOpenAccountCallback(final CallbackBuilder callbackBuilder, final String accountNumber,
                                         final Long cardNumber, final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("iBAN", accountNumber);
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    private void sendAccessRequestCallback(final CallbackBuilder callbackBuilder, final Long cardNumber,
                                           final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    private void openAdditionalAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                       final Object id) {
        String cookie = (String) params.get("authToken");
        uiClient.putFormAsyncWith1Param("/services/ui/account/new", "cookie", cookie,
                                        (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Customer reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                        Customer.class);
                String accountNumber = reply.getAccount().getAccountNumber();
                System.out.printf("%s New Account creation successfull, Account Holder: %s,"
                                    + " AccountNumber: %s\n\n\n\n", PREFIX, reply.getCustomerId(),
                                    accountNumber);
                doNewPinCardRequest(accountNumber, cookie, callbackBuilder, id, true);
            } else {
                System.out.printf("%s Account creation failed. body: %s\n\n\n\n", PREFIX, body);
            }
        });
    }

    private void closeAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                              final Object id) {
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

    private void sendCloseAccountCallback(final CallbackBuilder callbackBuilder, final Object id,
                                          final String replyAccountNumber) {
        System.out.printf("%s Successfully closed account %s\n\n\n\n", PREFIX, replyAccountNumber);
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    private void provideAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        // does an account Link to a username(so we need a conversion for this internally)
        // then performs a new pin card request for the customer with username.
        AccountLink request = JSONParser.createJsonAccountLink((String) params.get("iBAN"),
                                                                (String) params.get("username"), false);
        System.out.printf("%s Sending account link request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/account", "request",
                jsonConverter.toJson(request), "cookie", params.get("authToken"),
                (code, contentType, body) -> {
            if (code == HTTP_OK) {
                AccountLink reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                            AccountLink.class);
                if (reply.isSuccessful()) {
                    String accountNumber = reply.getAccountNumber();
                    System.out.printf("%s Account link successfull for Account Holder: %s, AccountNumber: %s\n\n\n\n",
                            PREFIX, reply.getCustomerId(), accountNumber);
                    //todo do new pin card request with cookie belonging to user with username.
                } else {
                    System.out.printf("%s Account link creation unsuccessfull.\n\n\n\n", PREFIX);
                }
            } else {
                System.out.printf("%s Account link creation failed.\n\n\n\n", PREFIX);
            }
        });
    }

    private void revokeAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                              final Object id) {
        //todo add functionality for account Link removal
        // performs an account Link removal and then removes the pincard(s) of said customer.
        // look at documentation for more specifics.
    }

    private void depositIntoAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                    final Object id) {
        PinTransaction pin = JSONParser.createJsonPinTransaction(ATMNUMBER, (String) params.get("iBAN"),
                "", (String) params.get("pinCode"), (Long) params.get("pinCard"),
                (Double) params.get("amount"), true);
        System.out.printf("%s Sending pin transaction.\n", PREFIX);
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "request",
                jsonConverter.toJson(pin), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.printf("%s ATM transaction successfull.\n\n\n", PREFIX);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s ATM transaction couldn't be processed.\n\n\n", PREFIX);
                            //todo figure out how to fetch what cause the failed processing.
                        } else {
                            System.out.printf("%s ATM transaction was not successfull.\n\n\n", PREFIX);
                            //todo send unsuccessfull reply, find way to fetch cause of this.
                        }
                    } else {
                        System.out.printf("%s ATM transaction request failed.\n\n\n", PREFIX);
                        //todo figure out a way to find out what made the request fail.
                    }
                });
    }

    private void payFromAccount(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                final Object id) {
        PinTransaction pin = JSONParser.createJsonPinTransaction((String) params.get("sourceIBAN"),
                (String) params.get("targetIBAN"), "", (String) params.get("pinCode"),
                (Long) params.get("pinCard"), (Double) params.get("amount"), false);
        System.out.printf("%s Sending pin transaction.\n", PREFIX);
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "request",
                jsonConverter.toJson(pin), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                   Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.printf("%s Pin transaction successfull.\n\n\n", PREFIX);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Pin transaction couldn't be processed.\n\n\n", PREFIX);
                            //todo figure out how to fetch what cause the failed processing.
                        } else {
                            System.out.printf("%s Pin transaction was not successfull.\n\n\n", PREFIX);
                            //todo send unsuccessfull reply, find way to fetch cause of this.
                        }
                    } else {
                        System.out.printf("%s Pin transaction request failed.\n\n\n", PREFIX);
                        //todo figure out a way to find out what made the request fail.
                    }
                });
    }

    private void transferMoney(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        Transaction transaction = JSONParser.createJsonTransaction(-1, (String) params.get("sourceIBAN"),
                (String) params.get("targetIBAN"), (String) params.get("targetName"),
                (String) params.get("description"), (Double) params.get("amount"), false, false);
        System.out.printf("%s Sending internal transaction.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/transaction", "request",
                jsonConverter.toJson(transaction), "cookie", params.get("authToken"),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                   Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            long transactionId = reply.getTransactionID();
                            System.out.printf("%s Internal transaction %d successfull.\n\n\n\n",
                                    PREFIX, transactionId);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Internal transaction couldn't be processed\n\n\n\n", PREFIX);
                            //todo figure out how to fetch what cause the failed processing.
                        } else {
                            System.out.printf("%s Internal transaction was not successfull\n\n\n\n", PREFIX);
                            //todo send unsuccessfull reply, find way to fetch cause of this.
                        }
                    } else {
                        System.out.printf("%s Transaction request failed.\n\n\n\n", PREFIX);
                        //todo figure out a way to find out what made the request fail.
                    }
                });
    }

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
                        System.out.printf("%s Successfull login, set the following cookie: %s\n\n\n\n",
                                PREFIX, authenticationReply.getCookie());
                        Map<String, Object> result = new HashMap<>();
                        result.put("authToken", authenticationReply.getCookie());
                        JSONRPC2Response response = new JSONRPC2Response(result, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s Login failed.\n\n\n\n", PREFIX);
                        //todo return error.
                    }
                });
    }
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
                System.out.printf("%s Request successfull, balance: %f\n\n\n\n", PREFIX,
                        balanceReply.getAccountData().getBalance());
                Map<String, Object> result = new HashMap<>();
                result.put("balance", balanceReply.getAccountData().getBalance());
                JSONRPC2Response response = new JSONRPC2Response(result, id);
                callbackBuilder.build().reply(response.toJSONString());
            } else {
                System.out.printf("%s Request not successfull, body: %s\n", PREFIX, body);
                //todo return error.
            }
        });
    }

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
                        System.out.printf("%s TransactionOverview request successfull.\n\n\n\n", PREFIX);
                        List<Map<String, Object>> transactionList = new ArrayList<>();
                        balanceReply.getTransactions().subList(0, (int) params.get("nrOfTransactions")).forEach(k -> {
                            Map<String, Object> transaction = new HashMap<>();
                            transaction.put("sourceIBAN", k.getSourceAccountNumber());
                            transaction.put("targetIBAN", k.getDestinationAccountNumber());
                            transaction.put("targetName", k.getDestinationAccountHolderName());
                            transaction.put("date", k.getTimestamp());
                            transaction.put("amount", k.getTransactionAmount());
                            transaction.put("description", k.getDescription());
                            transactionList.add(transaction);
                        });
                        JSONRPC2Response response = new JSONRPC2Response(transactionList, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s Request not successfull, body: %s\n", PREFIX, body);
                        //todo return error.
                    }
                });
    }
    private void getUserAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.ACCOUNTS, 0L);
        System.out.printf("%s Sending transactionOverview request.\n", PREFIX);
        uiClient.getAsyncWith2Params("/services/ui/data", "request", jsonConverter.toJson(request),
                "cookie", params.get("authToken"), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        DataReply accountsReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                         DataReply.class);
                        System.out.printf("%s Accounts request successfull.\n\n\n\n", PREFIX);
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
                        System.out.printf("%s Request not successfull, body: %s\n", PREFIX, body);
                        //todo return error.
                    }
                });
    }
    private void getBankAccountAccess(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                      final Object id) {
        // not yet in the system functionality, will need to be added.
    }
}
