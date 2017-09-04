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
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

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
    /** Number of the ATM system for internal use. */
    public static final String ATMNUMBER = "NL52GNIB3676451168";
    /** Used to check if accountNumber are of the correct length. */
    public static final int MAX_ACCOUNT_NUMBER_LENGTH = 19;
    /** Character limit used to check if a fields value is too long. */
    public static final int CHARACTER_LIMIT = 50;
    /** Character limit used to check if a transaction description is too long. */
    public static final int DESCRIPTION_LIMIT = 200;

    /**
     * Constructor.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    ApiService(final int servicePort, final String serviceHost,
                      final int sysInfoPort, final String sysInfoHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
        this.jsonConverter = new Gson();
        sendServiceInformation(servicePort, serviceHost);
    }

    /**
     * Method that sends the service information of this service to the SystemInformationService.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     */
    private void sendServiceInformation(final int servicePort, final String serviceHost) {
        ServiceInformation serviceInfo = new ServiceInformation(servicePort, serviceHost, ServiceType.API_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", jsonConverter.toJson(serviceInfo), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        System.err.println("Problem with connection to the SystemInformationService.");
                        System.err.println("Shutting down the Api service.");
                        System.exit(1);
                    }
        });
    }

    /**
     * Method that initializes all connections to other services once it knows their addresses.
     * @param callback Callback to the source of the request.
     * @param systemInfo Json string containing all System Information.
     */
    @RequestMapping(value = "/start", method = RequestMethod.PUT)
    public void startService(final Callback<String> callback, @RequestParam("sysInfo") final String systemInfo) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(systemInfo), MessageWrapper.class);

        SystemInformation sysInfo = (SystemInformation) messageWrapper.getData();
        ServiceInformation pin = sysInfo.getPinServiceInformation();
        ServiceInformation authentication = sysInfo.getAuthenticationServiceInformation();

        this.pinClient = httpClientBuilder().setHost(pin.getServiceHost())
                .setPort(pin.getServicePort()).buildAndStart();
        this.authenticationClient = httpClientBuilder().setHost(authentication.getServiceHost())
                .setPort(authentication.getServicePort()).buildAndStart();

        System.out.printf("%s Initialization of Api service connections complete.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
    }

    /**
     * Parses the request and then calls the logging method.
     * @param callback Callback to the source of the request.
     * @param requestJson Json string containing the request that was made.
     */
    @RequestMapping(value = "/request", method = RequestMethod.POST)
    public void handleApiRequest(final Callback<String> callback, final String requestJson) {
        try {
            JSONRPC2Request request = JSONRPC2Request.parse(requestJson);
            sendLogEvent(request, callback);
        } catch (JSONRPC2ParseException e) {
            callback.reply(new JSONRPC2Response(JSONRPC2Error.PARSE_ERROR).toJSONString());
        }
    }

    /**
     * Logs a request in the systemInfo service and then forwards the request to the according request handler.
     * @param request Request to log and forward.
     * @param callback Used to send the result of the request back to the request source.
     */
    private void sendLogEvent(final JSONRPC2Request request, final Callback<String> callback) {
        System.out.printf("%s Sending event log to SysInfo\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/log/request",
                "request", jsonConverter.toJson(request), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        forwardApiRequest(request, callback);
                    } else {
                        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                                500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Forwards the request to the correct request handler.
     * @param request Request to forward.
     * @param callback Used to send the result of the request back to the request source.
     */
    private void forwardApiRequest(final JSONRPC2Request request, final Callback<String> callback) {
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
            case "reset":                   Reset.reset(params, api);
                break;
            case "getDate":                 GetDate.getDate(params, api);
                break;
            case "setOverdraftLimit":       SetOverdraftLimit.setOverdraftLimit(params, api);
                break;
            case "getOverdraftLimit":       GetOverdraftLimit.getOverdraftLimit(params, api);
                break;
            case "getEventLogs":            GetEventLogs.getEventLogs(params, api);
                break;
            case "openSavingsAccount":      OpenSavingsAccount.openSavingsAccount(params, api);
                break;
            case "closeSavingsAccount":     CloseSavingsAccount.closeSavingsAccount(params, api);
                break;
            case "invalidateCard":          InvalidateCard.invalidateCard(params, api);
                break;
            case "requestCreditCard":       RequestCreditCard.requestCreditCard(params, api);
                break;
            case "setFreezeUserAccount":    SetFreezeUserAccount.setFreezeUserAccount(params, api);
                break;
            case "transferBankAccount":     TransferBankAccount.transferBankAccount(params, api);
                break;
            default:
                System.out.println(request.getMethod());
                callback.reply(new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, request.getID()).toJSONString());
                break;
        }
    }

    /**
     * Returns the connection to the Pin Service.
     * @return The connection
     */
    public HttpClient getPinClient() {
        return pinClient;
    }

    /**
     * Returns the connection to the System Information Service.
     * @return The connection
     */
    public HttpClient getSystemInformationClient() {
        return systemInformationClient;
    }

    /**
     * Returns the connection to the Authentication Service.
     * @return The connection
     */
    public HttpClient getAuthenticationClient() {
        return authenticationClient;
    }

    /**
     * Returns jsonConverter.
     * @return The jsonConverter
     */
    public Gson getJsonConverter() {
        return jsonConverter;
    }
}
