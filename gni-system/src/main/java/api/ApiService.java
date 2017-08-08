package api;

import api.methods.CloseAccount;
import api.methods.GetAuthToken;
import api.methods.OpenAccount;
import api.methods.OpenAdditionalAccount;
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
import databeans.MessageWrapper;
import util.JSONParser;

import java.time.LocalDate;
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
public class ApiService {
    /** Connection to the pin service. */
    private HttpClient pinClient;
    /** Connection to the SystemInformation service. */
    private HttpClient systemInformationClient;
    /** Connection to the authentication service. */
    private HttpClient authenticationClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Api Service. */
    public static final String PREFIX = "[API]                 :";
    /** Number of the ATM system for internal use*/
    public static final String ATMNUMBER = "NL52GNIB3676451168";
    /** Used to check if accountNumber are of the correct length. */
    public static final int accountNumberLength = 18;
    /** Character limit used to check if a fields value is too long. */
    public static final int characterLimit = 50;
    /** Character limit used to check if a transaction description is too long. */
    public static final int descriptionLimit = 200;

    /**
     * Constructor
     * @param authenticationPort Port the ui service is located on.
     * @param authenticationHost Host the ui service is located on.
     * @param pinPort Port the pin service is located on.
     * @param pinHost Host the pin service is located on.
     */
    public ApiService(final int authenticationPort, final String authenticationHost, final int pinPort, final String pinHost,
                      final int sysInfoPort, final String sysInfoHost) {
        pinClient = httpClientBuilder().setHost(pinHost).setPort(pinPort).buildAndStart();
        systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
        authenticationClient = httpClientBuilder().setHost(authenticationHost).setPort(authenticationPort)
                .buildAndStart();
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
            ApiBean api = new ApiBean(this, callbackBuilder, id);
            switch (method) {
                case "openAccount":             OpenAccount.openAccount(params, api);
                    break;
                case "openAdditionalAccount":   OpenAdditionalAccount.openAdditionalAccount(params, api);
                    break;
                case "closeAccount":            CloseAccount.closeAccount(params, api);
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
                case "getAuthToken":            GetAuthToken.getAuthToken(params, api);
                    break;
                case "getBalance":              getBalance(params, callbackBuilder, id);
                    break;
                case "getTransactionsOverview": getTransactionsOverview(params, callbackBuilder, id);
                    break;
                case "getUserAccess":           getUserAccess(params, callbackBuilder, id);
                    break;
                case "getBankAccountAccess":    getBankAccountAccess(params, callbackBuilder, id);
                    break;
                case "unblockCard":             unblockCard(params, callbackBuilder, id);
                    break;
                case "simulateTime":            simulateTime(params, callbackBuilder, id);
                    break;
                case "reset":                   reset(callbackBuilder, id);
                    break;
                case "getDate":                 getDate(callbackBuilder, id);
                    break;
                default:                        callback.reply(new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND,
                        request.getID()).toJSONString());
                    break;
            }
        } catch (JSONRPC2ParseException e) {
            callback.reply(new JSONRPC2Response(JSONRPC2Error.PARSE_ERROR).toJSONString());
        }
    }

    public HttpClient getPinClient() {
        return pinClient;
    }

    public HttpClient getSystemInformationClient() {
        return systemInformationClient;
    }

    public HttpClient getAuthenticationClient() {
        return authenticationClient;
    }

    public Gson getJsonConverter() {
        return jsonConverter;
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
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    String message = (String) messageWrapper.getData();
                    System.out.printf("%s Account link removal successful for Account Holder: %s, AccountNumber: %s\n\n\n\n", PREFIX, message, accountNumber);
                    sendRevokeAccessCallback(callbackBuilder, id);
                } else {
                    System.out.printf("%s Account link removal unsuccessful.\n\n\n\n", PREFIX);
                    sendErrorReply(callbackBuilder, messageWrapper, id);
                }
            } else {
                System.out.printf("%s Account link removal failed, body: %s\n\n\n\n", PREFIX, body);
                JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                callbackBuilder.build().reply(response.toJSONString());
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Transaction reply = (Transaction) messageWrapper.getData();
                            if (reply.isSuccessful() && reply.isProcessed()) {
                                System.out.printf("%s ATM transaction successful.\n\n\n", PREFIX);
                                Map<String, Object> result = new HashMap<>();
                                JSONRPC2Response response = new JSONRPC2Response(result, id);
                                callbackBuilder.build().reply(response.toJSONString());
                            } else {
                                System.out.printf("%s ATM transaction was not successful.\n\n\n", PREFIX);
                                sendErrorReply(callbackBuilder, JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), id);
                            }
                        } else {
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s ATM transaction request failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Transaction reply = (Transaction) messageWrapper.getData();
                            if (reply.isSuccessful() && reply.isProcessed()) {
                                System.out.printf("%s Pin transaction successful.\n\n\n", PREFIX);
                                Map<String, Object> result = new HashMap<>();
                                JSONRPC2Response response = new JSONRPC2Response(result, id);
                                callbackBuilder.build().reply(response.toJSONString());
                            } else {
                                System.out.printf("%s Pin transaction was not successful.\n\n\n", PREFIX);
                                sendErrorReply(callbackBuilder, JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), id);
                            }
                        } else {
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s Pin transaction request failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Transaction reply = (Transaction) messageWrapper.getData();
                            if (reply.isSuccessful() && reply.isProcessed()) {
                                long transactionId = reply.getTransactionID();
                                System.out.printf("%s Internal transaction %d successful.\n\n\n\n",
                                        PREFIX, transactionId);
                                Map<String, Object> result = new HashMap<>();
                                JSONRPC2Response response = new JSONRPC2Response(result, id);
                                callbackBuilder.build().reply(response.toJSONString());
                            } else {
                                System.out.printf("%s Internal transaction was not successful\n\n\n\n", PREFIX);
                                sendErrorReply(callbackBuilder, JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), id);
                            }
                        } else {
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s Transaction request failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
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
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    DataReply balanceReply = (DataReply) messageWrapper.getData();
                    System.out.printf("%s Request successful, balance: %f\n\n\n\n", PREFIX,
                            balanceReply.getAccountData().getBalance());
                    Map<String, Object> result = new HashMap<>();
                    result.put("balance", balanceReply.getAccountData().getBalance());
                    JSONRPC2Response response = new JSONRPC2Response(result, id);
                    callbackBuilder.build().reply(response.toJSONString());
                } else {
                    sendErrorReply(callbackBuilder, messageWrapper, id);
                }
            } else {
                System.out.printf("%s Request not successful, body: %s\n\n\n\n", PREFIX, body);
                JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                callbackBuilder.build().reply(response.toJSONString());
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            DataReply balanceReply = (DataReply) messageWrapper.getData();
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
                                transactionMap.put("date", transaction.getDate().toString());
                                transactionMap.put("amount", transaction.getTransactionAmount());
                                transactionMap.put("description", transaction.getDescription());
                                transactionList.add(transactionMap);
                            }
                            JSONRPC2Response response = new JSONRPC2Response(transactionList, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else {
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s Request not successful, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            DataReply accountsReply = (DataReply) messageWrapper.getData();
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
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s Accounts request not successful, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            DataReply accountsReply = (DataReply) messageWrapper.getData();
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
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s BankAccountAccess Request not successful, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
                    }
                });
    }

    /**
     * Unblocks a blocked pin card, requires logging in.
     * @param params Parameters of the request(authToken, iBAN, pinCard).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void unblockCard(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                               final Object id) {
        String accountNumber = (String) params.get("iBAN");
        String pinCard = (String) params.get("pinCard");
        String cookie = (String) params.get("authToken");
        PinCard request = JSONParser.createJsonPinCard(accountNumber, Long.parseLong(pinCard), null, 0L, null);
        System.out.printf("%s Sending pinCard unblock request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/unblockCard", "request",
                jsonConverter.toJson(request), "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            System.out.printf("%s PinCard unblocked successfully for AccountNumber: %s, CardNumber: %s\n\n\n\n",
                                    PREFIX, accountNumber, pinCard);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else {
                            System.out.printf("%s PinCard unblocking unsuccessful.\n\n\n\n", PREFIX);
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s PinCard unblocking failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
                    }
                });
    }

    /**
     * Process passing of time withint the system.
     * @param params Parameters of the request(nrOfDays).
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void simulateTime(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                             final Object id) {
        Long nrOfDays = (Long) params.get("nrOfDays");
        System.out.printf("%s Sending simulate time request.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/date/increment", "days",
                jsonConverter.toJson(nrOfDays), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            System.out.printf("%s %s days have now passed on the system.\n\n\n\n", PREFIX, "" + nrOfDays);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else {
                            System.out.printf("%s Simulate time request unsuccessful.\n\n\n\n", PREFIX);
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s Simulate time request unsuccessful.\n\n\n\n", PREFIX);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
                    }
                });
    }

    /**
     * Resets the system's Database and system time.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void reset(final CallbackBuilder callbackBuilder, final Object id) {
        System.out.printf("%s Sending Reset request.\n", PREFIX);
        systemInformationClient.postAsync("/services/systemInfo/reset", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    LocalDate date = (LocalDate) messageWrapper.getData();
                    System.out.printf("%s Reset successful, the current date is: %s\n\n\n\n", PREFIX, date.toString());
                    Map<String, Object> result = new HashMap<>();
                    JSONRPC2Response response = new JSONRPC2Response(result, id);
                    callbackBuilder.build().reply(response.toJSONString());
                } else {
                    System.out.printf("%s Reset unsuccessful.\n\n\n\n", PREFIX);
                    sendErrorReply(callbackBuilder, messageWrapper, id);
                }
            } else {
                System.out.printf("%s Reset request failed, body: %s\n\n\n\n", PREFIX, body);
                JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                callbackBuilder.build().reply(response.toJSONString());
            }
        });
    }

    /**
     * Requests the current system date.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     * @param id Id of the request.
     */
    private void getDate(final CallbackBuilder callbackBuilder, final Object id) {
        System.out.printf("%s Sending current date request.\n", PREFIX);
        systemInformationClient.getAsync("/services/systemInfo/date", (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            LocalDate date = (LocalDate) messageWrapper.getData();
                            System.out.printf("%s Current date successfully queried, the current date is: %s\n\n\n\n",
                                    PREFIX, date.toString());
                            Map<String, Object> result = new HashMap<>();
                            result.put("date", date.toString());
                            JSONRPC2Response response = new JSONRPC2Response(result, id);
                            callbackBuilder.build().reply(response.toJSONString());
                        } else {
                            System.out.printf("%s Date request unsuccessful.\n\n\n\n", PREFIX);
                            sendErrorReply(callbackBuilder, messageWrapper, id);
                        }
                    } else {
                        System.out.printf("%s Date request unblocking failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), id);
                        callbackBuilder.build().reply(response.toJSONString());
                    }
                });
    }
}
