package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class GetOverdraftLimit {

    /**
     * Gets current overdraft limit for a certain account.
     * @param params Map containing the parameters of the request (authToken, IBAN).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getOverdraftLimit(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Sending close account request.\n", PREFIX);
        doGetOverdraftRequest((String) params.get("iBAN"), (String) params.get("authToken"), api);
    }

    /**
     * Forwards the getOverdraftLimit request to the Authentication Service and sends a callback if the request is
     * successful, or sends an error if the request fails.
     * @param accountNumber AccountNumber of the account for which the new limit has to be queried.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doGetOverdraftRequest(final String accountNumber, final String cookie, final ApiBean api) {
        System.out.printf("%s Forwarding getOverdraft request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith2Params("/services/authentication/overdraft/get",
                "accountNumber", accountNumber, "cookie", cookie,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendGetOverdraftLimitCallback((Integer) messageWrapper.getData(), api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests"), api);
                    }
                });
    }

    /**
     * Sends callback back to the source of the request.
     * @param overdraftLimit The current overdraft limit
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendGetOverdraftLimitCallback(final int overdraftLimit, final ApiBean api) {
        System.out.printf("%s Successfully queried the current overdraft limit: %d.\n\n\n\n", PREFIX, overdraftLimit);
        Map<String, Object> result = new HashMap<>();
        result.put("overdraftLimit", overdraftLimit);
        api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
    }
}
