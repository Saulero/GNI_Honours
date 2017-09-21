package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;
import databeans.MethodType;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.ApiService.MAX_ACCOUNT_NUMBER_LENGTH;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public abstract class CloseAccount {

    /**
     * Removes an account from the system.
     * @param params Map containing the parameters of the request (authToken, IBAN).
     * @param api DataBean containing everything in the ApiService
     */
    public static void closeAccount(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Sending close account request.\n", PREFIX);
        handleAccountRemovalExceptions((String) params.get("iBAN"), (String) params.get("authToken"), api);
    }

    /**
     * Tries to verify the input of an account removal request and then forward the request, sends a rejection if an
     * exception occurs.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleAccountRemovalExceptions(
            final String accountNumber, final String cookie, final ApiBean api) {
        try {
            verifyAccountRemovalInput(accountNumber, cookie);
            doAccountRemovalRequest(accountNumber, cookie, api);
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        }
    }

    /**
     * Checks if the input for an account removal request is correctly formatted and contains correct values.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     */
    private static void verifyAccountRemovalInput(final String accountNumber, final String cookie)
            throws IncorrectInputException {
        if (accountNumber == null || accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        } else if (cookie == null) {
            throw new IncorrectInputException("The following variable was incorrectly specified: authToken.");
        }
    }

    /**
     * Forwards the account removal request to the Authentication Service and sends a callback if the request is
     * successful, or sends a rejection if the request fails.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doAccountRemovalRequest(
            final String accountNumber, final String cookie, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.CLOSE_ACCOUNT);
        data.setData(accountNumber);

        System.out.printf("%s Forwarding account removal request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/account/remove",
                "data", api.getJsonConverter().toJson(data),
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendCloseAccountCallback((AccountLink) messageWrapper.getData(), api);
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
     * Sends te result of the closeAccountRequest back to the request source using a JSONRPC object.
     * @param reply Used to show which accountNumber is closed.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendCloseAccountCallback(final AccountLink reply, final ApiBean api) {
        System.out.printf("%s Successfully closed account %s\n\n\n\n", PREFIX, reply.getAccountNumber());
        Map<String, Object> result = new HashMap<>();
        api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
    }
}
