package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import databeans.AccountLink;
import databeans.MessageWrapper;
import databeans.MethodType;
import util.JSONParser;

import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.NewPinCard.doNewPinCardRequest;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static api.methods.SharedUtilityMethods.verifyAccountLinkInput;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class ProvideAccess {

    /**
     * Links an account to the user with the username specified in params. Then creates a new pin card for the user
     * and returns the pincard and pincode.
     * @param params Parameters of the request(authToken, iBAN, username).
     * @param api DataBean containing everything in the ApiService
     */
    public static void provideAccess(final Map<String, Object> params, final ApiBean api) {
        // does an account Link to a username(so we need a conversion for this internally)
        // then performs a new pin card request for the customer with username.
        String accountNumber = (String) params.get("iBAN");
        String username = (String) params.get("username");
        String cookie = (String) params.get("authToken");
        AccountLink accountLink = JSONParser.createJsonAccountLink(accountNumber, username, false);
        System.out.printf("%s Sending account link request.\n", PREFIX);
        handleAccountLinkExceptions(accountLink, cookie, api);
    }

    /**
     * Tries to verify the input of the accountLink request and then forward the request, send a rejection if an
     * exception is thrown.
     * @param accountLink {@link AccountLink} that should be created in the system.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleAccountLinkExceptions(
            final AccountLink accountLink, final String cookie, final ApiBean api) {
        try {
            verifyAccountLinkInput(accountLink);
            doAccountLinkRequest(accountLink, cookie, api);
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
     * Forwards a String representing an account link to the Authentication Service, and processes the reply if it is
     * successful or sends a rejection to the requesting source if it fails.
     * @param accountLink {@link AccountLink} that should be executed.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doAccountLinkRequest(final AccountLink accountLink, final String cookie, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.PROVIDE_ACCESS);
        data.setData(accountLink);

        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/accountLink",
                "data", api.getJsonConverter().toJson(data),
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(accountLinkReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendAccountLinkRequestCallback((AccountLink) messageWrapper.getData(), cookie, api);
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
     * Forwards the result of an account link request to the service that sent the request.
     * @param accountLink The result of an account link request.
     * @param cookie Cookie of the user that sent the data request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendAccountLinkRequestCallback(
            final AccountLink accountLink, final String cookie, final ApiBean api) {
        System.out.printf("%s Successful account link, sending callback.\n", PREFIX);
        System.out.printf("%s Account link successful for Account Holder: %s, AccountNumber: %s\n\n\n\n",
                PREFIX, accountLink.getCustomerId(), accountLink.getAccountNumber());
        doNewPinCardRequest(MethodType.PROVIDE_ACCESS, accountLink.getAccountNumber(), accountLink.getUsername(), cookie, api, false);
    }
}
