package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static api.methods.SharedUtilityMethods.verifyAccountLinkInput;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class RevokeAccess {

    /**
     * Removes a users access to an account based on the username specified.
     * @param params Parameters of the request (authToken, iBAN, username).
     * @param api DataBean containing everything in the ApiService
     */
    public static void revokeAccess(final Map<String, Object> params, final ApiBean api) {
        // performs an account Link removal and then removes the pincard(s) of said customer.
        // look at documentation for more specifics.
        String accountNumber = (String) params.get("iBAN");
        String username = (String) params.get("username");
        String cookie = (String) params.get("authToken");
        AccountLink accountLink = JSONParser.createJsonAccountLink(accountNumber, username, false);
        System.out.printf("%s Sending account link removal request.\n", PREFIX);
        handleAccountLinkRemovalExceptions(accountLink, cookie, api);
    }

    /**
     * Tries to parse the accountLink and verifies it contains a correct accountNumber. Then forwards the request to
     * the authenticationService.
     * @param accountLink {@link AccountLink} that should be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleAccountLinkRemovalExceptions(
            final AccountLink accountLink, final String cookie, final ApiBean api) {
        try {
            verifyAccountLinkInput(accountLink);
            doAccountLinkRemoval(accountLink, cookie, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", e.getMessage()), api);
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), api);
        }
    }

    /**
     * Forwards a String representing an account link that is to be removed from the system to the Authentication
     * Service, and processes the reply if it is successful or sends a rejection to the requesting source if it fails.
     * @param accountLink {@link AccountLink} that should be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doAccountLinkRemoval(
            final AccountLink accountLink, final String cookie, final ApiBean api) {
        api.getAuthenticationClient().putFormAsyncWith2Params("/services/authentication/accountLink/remove",
                "request", api.getJsonConverter().toJson(accountLink), "cookie", cookie,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(accountLinkReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendAccountLinkRemovalCallback((String) messageWrapper.getData(), api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests"), api);
                    }
                }));
    }

    /**
     * Forwards the result of an account link removal to the service that sent the request.
     * @param customerId The result of an account link removal.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendAccountLinkRemovalCallback(final String customerId, final ApiBean api) {
        System.out.printf("%s Account link removal successful for CustomerId: %s\n\n\n\n", PREFIX, customerId);
        sendRevokeAccessCallback(api);
    }

    /**
     * Sends te result of the revokeAccess request back to the request source using a JSONRPC object.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendRevokeAccessCallback(final ApiBean api) {
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
