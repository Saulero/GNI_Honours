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
public class SetOverdraftLimit {

    /**
     * Sets new overdraft limit for a certain account.
     * @param params Map containing the parameters of the request (authToken, IBAN, overdraftLimit).
     * @param api DataBean containing everything in the ApiService
     */
    public static void setOverdraftLimit(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Sending set overdraft limit request.\n", PREFIX);
        verifyOverdraftInput((String) params.get("iBAN"),
                (String) params.get("authToken"),
                (Double) params.get("overdraftLimit"), api);
    }

    /**
     * Tries to verify the input of setOverdraftLimit request and then forward the request.
     * @param accountNumber AccountNumber of the account for which the new limit has to be set.
     * @param cookie Cookie of the User that sent the request.
     * @param overdraftLimit The new overdraft limit
     * @param api DataBean containing everything in the ApiService
     */
    private static void verifyOverdraftInput(
            final String accountNumber, final String cookie, final Double overdraftLimit, final ApiBean api) {
        if (overdraftLimit < 0 || overdraftLimit > 5000) {
            sendErrorReply(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "The new limit is not >0 and <5000"), api);
        } else {
            doSetOverdraftRequest(accountNumber, cookie, overdraftLimit, api);
        }
    }

    /**
     * Forwards the setOverdraftLimit request to the Authentication Service and sends a callback if the request is
     * successful, or sends an error if the request fails.
     * @param accountNumber AccountNumber of the account for which the new limit has to be set.
     * @param cookie Cookie of the User that sent the request.
     * @param overdraftLimit The new overdraft limit
     * @param api DataBean containing everything in the ApiService
     */
    private static void doSetOverdraftRequest(
            final String accountNumber, final String cookie, final Double overdraftLimit, final ApiBean api) {
        System.out.printf("%s Forwarding setOverdraft request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith3Params("/services/authentication/overdraft/set",
                "accountNumber", accountNumber, "cookie", cookie, "overdraftLimit", overdraftLimit,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendSetOverdraftLimitCallback(api);
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
    private static void sendSetOverdraftLimitCallback(final ApiBean api) {
        System.out.printf("%s Successfully set the new overdraft limit.\n\n\n\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
    }
}
