package api;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.Authentication;
import databeans.Customer;
import databeans.PinCard;
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
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Api Service. */
    private static final String PREFIX = "[API]                 :";

    public ApiService(final int uiPort, final String uiHost) {
        uiClient = httpClientBuilder().setHost(uiHost).setPort(uiPort).buildAndStart();
        jsonConverter = new Gson();
    }

    @RequestMapping(value = "/request")
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
                /*case "openAdditionalAccount":   callback.reply(openAdditionalAccountHandler(params).toJSONString());
                    break;
                case "closeAccount":            callback.reply(closeAccountHandler(params).toJSONString());
                    break;
                case "provideAccess":           callback.reply(provideAccessHandler(params).toJSONString());
                    break;
                case "revokeAccess":            callback.reply(revokeAccessHandler(params).toJSONString());
                    break;
                case "depositIntoAccount":      callback.reply(depositIntoAccountHandler(params).toJSONString());
                    break;
                case "payFromAccount":          callback.reply(payFromAccountHandler(params).toJSONString());
                    break;
                case "transferMoney":           callback.reply(transferMoneyHandler(params).toJSONString());
                    break;
                case "getAuthToken":            callback.reply(getAuthTokenHandler(params).toJSONString());
                    break;
                case "getBalance":              callback.reply(getBalanceHandler(params).toJSONString());
                    break;
                case "getTransactionsOverview": callback.reply(getTransactionsOverviewHandler(params).toJSONString());
                    break;
                case "getUserAccess":           callback.reply(getUserAccessHandler(params).toJSONString());
                    break;
                case "getBankAccountAccess":    callback.reply(getBankAccountAccessHandler(params).toJSONString());
                    break;*/
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
                    }
                });
    }

    private void sendOpenAccountCallback(final CallbackBuilder callbackBuilder, final String accountNumber,
                                         final Long cardNumber, final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("iBAN", accountNumber);
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /*private JSONRPC2Response openAdditionalAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response closeAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response provideAccessHandler(final Map<String, Object> params) {}
    private JSONRPC2Response revokeAccessHandler(final Map<String, Object> params) {}
    private JSONRPC2Response depositIntoAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response payFromAccountHandler(final Map<String, Object> params) {}
    private JSONRPC2Response transferMoneyHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getAuthTokenHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getBalanceHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getTransactionsOverviewHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getUserAccessHandler(final Map<String, Object> params) {}
    private JSONRPC2Response getBankAccountAccessHandler(final Map<String, Object> params) {}*/
}
