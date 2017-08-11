package api;

import api.methods.*;
import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.ServiceInformation;
import databeans.ServiceType;
import databeans.SystemInformation;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
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
    public static final int ACCOUNT_NUMBER_LENGTH = 18;
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
                "serviceInfo", serviceInfo, (httpStatusCode, httpContentType, replyJson) -> {
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
     * @param body Json string containing the request that was made.
     */
    @RequestMapping(value = "/start", method = RequestMethod.POST)
    public void startService(final Callback<String> callback, final String body) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);

        SystemInformation sysInfo = (SystemInformation) messageWrapper.getData();
        ServiceInformation pin = sysInfo.getPinServiceInformation();
        ServiceInformation authentication = sysInfo.getAuthenticationServiceInformation();

        this.pinClient = httpClientBuilder().setHost(pin.getServiceHost())
                .setPort(pin.getServicePort()).buildAndStart();
        this.authenticationClient = httpClientBuilder().setHost(authentication.getServiceHost())
                .setPort(authentication.getServicePort()).buildAndStart();

        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
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
                case "reset":                   Reset.reset(api);
                    break;
                case "getDate":                 GetDate.getDate(api);
                    break;
                case "setOverdraftLimit":       SetOverdraftLimit.setOverdraftLimit(params, api);
                    break;
                case "getOverdraftLimit":       GetOverdraftLimit.getOverdraftLimit(params, api);
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
