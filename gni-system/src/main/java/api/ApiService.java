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

import java.util.HashMap;
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
                case "openAccount":             openAccountHandler(params, callbackBuilder, id);
                    break;
                case "openAdditionalAccount":   openAdditionalAccountHandler(params, callbackBuilder, id);
                    break;
                case "closeAccount":            closeAccountHandler(params, callbackBuilder, id);
                    break;
                case "provideAccess":           provideAccessHandler(params, callbackBuilder, id);
                    break;
                case "revokeAccess":            revokeAccessHandler(params, callbackBuilder, id);
                    break;
                case "depositIntoAccount":      depositIntoAccountHandler(params, callbackBuilder, id);
                    break;
                case "payFromAccount":          payFromAccountHandler(params, callbackBuilder, id);
                    break;
                case "transferMoney":           transferMoneyHandler(params, callbackBuilder, id);
                    break;
                case "getAuthToken":            getAuthTokenHandler(params, callbackBuilder, id);
                    break;
                case "getBalance":              getBalanceHandler(params, callbackBuilder, id);
                    break;
                case "getTransactionsOverview": getTransactionsOverviewHandler(params, callbackBuilder, id);
                    break;
                case "getUserAccess":           getUserAccessHandler(params, callbackBuilder, id);
                    break;
                case "getBankAccountAccess":    getBankAccountAccessHandler(params, callbackBuilder, id);
                    break;
                default:                        callback.reply(new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND,
                        request.getID()).toJSONString());
                    break;
            }
        } catch (JSONRPC2ParseException e) {
            callback.reply(new JSONRPC2Response(JSONRPC2Error.PARSE_ERROR).toJSONString());
        }
    }

    private void openAccountHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
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
                        doLogin(customer.getUsername(), customer.getPassword(),
                                reply.getAccount().getAccountNumber(), callbackBuilder, id);
                    } else {
                        System.out.printf("%s Customer creation request failed, body: %s\n\n\n\n", PREFIX, replyJson);
                        //todo send error.
                    }
                });
    }

    private void doLogin(final String username, final String password, final String accountNumber,
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
                        doNewPinCardRequest(accountNumber, authenticationReply.getCookie(), callbackBuilder, id);
                    } else {
                        System.out.printf("%s Login failed.\n\n\n\n", PREFIX);
                        //todo send failed reply
                    }
                });
    }

    private void doNewPinCardRequest(final String accountNumber, final String cookie,
                                     final CallbackBuilder callbackBuilder, final Object id) {
        Gson gson = new Gson();
        uiClient.putFormAsyncWith2Params("/services/ui/card", "accountNumber", accountNumber,
                "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        PinCard newPinCard = gson.fromJson(JSONParser.removeEscapeCharacters(body), PinCard.class);
                        System.out.printf("%s Successfully requested a new pin card.\n\n\n\n", PREFIX);
                        sendOpenAccountCallback(callbackBuilder, accountNumber, newPinCard.getCardNumber(),
                                newPinCard.getPinCode(), id);
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

    private void openAdditionalAccountHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                              final Object id) {
        //todo needs modifications so users loads customerData during request as we currently require customer data to be specified.
    }

    private void closeAccountHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                     final Object id) {
        uiClient.putFormAsyncWith2Params("/services/ui/account/remove", "accountNumber",
                params.get("iBAN"), "cookie", params.get("authToken"), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        System.out.printf("%s Successfully closed account %s\n\n\n\n", PREFIX, body);
                        Map<String, Object> result = new HashMap<>();
                        JSONRPC2Response response = new JSONRPC2Response(result, id);
                        callbackBuilder.build().reply(response.toJSONString());
                    } else {
                        System.out.printf("%s Account closing failed.\n\n\n\n", PREFIX);
                    }
                });
    }

    private void provideAccessHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                      final Object id) {
        // does an account Link to a username(so we need a conversion for this internally)
        // then performs a new pin card request for the customer with username.
    }

    private void revokeAccessHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                     final Object id) {
        //todo add functionality for account Link removal
        // performs an account Link removal and then removes the pincard(s) of said customer.
        // look at documentation for more specifics.
    }

    private void depositIntoAccountHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                           final Object id) {

    }
    private void payFromAccountHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                       final Object id) {
        // can be implemented 1:1 with a pin transaction.
    }
    private void transferMoneyHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                      final Object id) {
        // can be implemented 1:1 with a normal transaction request.
    }
    private void getAuthTokenHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                     final Object id) {
        // can be implemented 1:1 with do login.
    }
    private void getBalanceHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                   final Object id) {
        // can be implemented 1:1 with get request.
    }
    private void getTransactionsOverviewHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                                final Object id) {
        // can be implemented 1:1 with get request, needs some more parsing in api.
    }
    private void getUserAccessHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                      final Object id) {
        // can be implemented 1:1 with get request.
    }
    private void getBankAccountAccessHandler(final Map<String, Object> params, final CallbackBuilder callbackBuilder,
                                             final Object id) {
        // not yet in the system functionality, will need to be added.
    }
}
