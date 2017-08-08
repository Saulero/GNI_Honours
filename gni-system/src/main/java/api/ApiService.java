package api;

import api.methods.*;
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
 * @author Saul & Noel
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
                case "provideAccess":           ProvideAccess.provideAccess(params, api);
                    break;
                case "revokeAccess":            RevokeAccess.revokeAccess(params, api);
                    break;
                case "depositIntoAccount":      DepositIntoAccount.depositIntoAccount(params, api);
                    break;
                case "payFromAccount":          PayFromAccount.payFromAccount(params, api);
                    break;
                case "transferMoney":           TransferMoney.transferMoney(params, api);
                    break;
                case "getAuthToken":            GetAuthToken.getAuthToken(params, api);
                    break;
                case "getBalance":              GetBalance.getBalance(params, api);
                    break;
                case "getTransactionsOverview": GetTransactionsOverview.getTransactionsOverview(params, api);
                    break;
                case "getUserAccess":           GetUserAccess.getUserAccess(params, api);
                    break;
                case "getBankAccountAccess":    GetBankAccountAccess.getBankAccountAccess(params, api);
                    break;
                case "unblockCard":             UnblockCard.unblockCard(params, api);
                    break;
                case "simulateTime":            SimulateTime.simulateTime(params, api);
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
