package api.methods;

import api.ApiBean;
import databeans.Customer;
import databeans.MessageWrapper;
import databeans.MethodType;
import util.JSONParser;

import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.NewPinCard.doNewPinCardRequest;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class OpenAdditionalAccount {

    /**
     * Opens an additional account for a customer.
     * @param params Map containing all the request parameters(authToken).
     * @param api DataBean containing everything in the ApiService
     */
    public static void openAdditionalAccount(final Map<String, Object> params, final ApiBean api) {
        doNewAccountRequest((String) params.get("authToken"), api);
    }

    /**
     * Forwards the cookie containing the customerId of the owner of the new account to the Authentication Service
     * and sends the result back to the request source, or rejects the request if the forwarding fails.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doNewAccountRequest(final String cookie, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.OPEN_ADDITIONAL_ACCOUNT);

        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/account/new",
                "data", api.getJsonConverter().toJson(data),
                (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(newAccountReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewAccountRequestCallback((Customer) messageWrapper.getData(), cookie, api);
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
     * Sends the result of an account creation request to the request source.
     * @param newAccountReply Customer with a linked account that was newly created.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendNewAccountRequestCallback(
            final Customer newAccountReply, final String cookie, final ApiBean api) {
        String accountNumber = newAccountReply.getAccount().getAccountNumber();
        System.out.printf("%s New Account creation successful, Account Holder: %s,"
                + " AccountNumber: %s\n\n\n\n", PREFIX, newAccountReply.getCustomerId(), accountNumber);
        doNewPinCardRequest(MethodType.OPEN_ADDITIONAL_ACCOUNT, accountNumber, "", cookie, api, true);
    }
}
