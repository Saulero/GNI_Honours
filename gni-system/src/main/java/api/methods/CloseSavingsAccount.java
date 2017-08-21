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
 * @author Noel
 */
public class CloseSavingsAccount {
    /**
     * Closes a savings account that is linked to an account in the system.
     * @param params Map containing the parameters for the request, must contain the authToken and iBAN parameters.
     * @param api DataBean containing everything in the ApiService
     */
    public static void closeSavingsAccount(final Map<String, Object> params, final ApiBean api) {
        String authToken = (String) params.get("authToken");
        String iBAN = (String) params.get("iBAN");
        if (authToken == null) {
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.",
                    "authToken not specified."), api);
        } else if (iBAN == null) {
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.",
                    "iBAN not specified."), api);
        } else {
            doCloseSavingsAccountRequest(authToken, iBAN, api);
        }
    }

    /**
     * Forwards the closeSavingsAccount request to the authentication service for processing.
     * @param authToken AuthToken of the customer that sent the request.
     * @param iBAN AccountNumber the savings account is linked to.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doCloseSavingsAccountRequest(final String authToken, final String iBAN, final ApiBean api) {
        api.getAuthenticationClient().putFormAsyncWith2Params("/services/authentication/savingsAccount/close",
                "authToken", authToken, "iBAN",
                iBAN, (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendCloseSavingsAccountCallback(api);
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
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendCloseSavingsAccountCallback(final ApiBean api) {
        System.out.printf("%s Successfully closed savings account.\n\n\n\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
    }
}
