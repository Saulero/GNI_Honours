package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.ApiService.accountNumberLength;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class CloseAccount {

    /**
     * Removes an account from the system.
     * @param params Map containing the parameters of the request (authToken, IBAN).
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
     */
    private static void handleAccountRemovalExceptions(
            final String accountNumber, final String cookie, final ApiBean api) {
        try {
            verifyAccountRemovalInput(accountNumber);
            doAccountRemovalRequest(accountNumber, cookie, api);
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        }
    }

    /**
     * Checks if the input for an account removal request is correctly formatted and contains correct values.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     */
    private static void verifyAccountRemovalInput(final String accountNumber) throws IncorrectInputException {
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards the account removal request to the Authentication Service and sends a callback if the request is
     * successful, or sends a rejection if the request fails.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     */
    private static void doAccountRemovalRequest(
            final String accountNumber, final String cookie, final ApiBean api) {
        System.out.printf("%s Forwarding account removal request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith2Params("/services/authentication/account/remove",
                "accountNumber", accountNumber, "cookie", cookie,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendCloseAccountCallback((AccountLink) messageWrapper.getData(), api);
                        } else {
                            api.getCallbackBuilder().build().reply(replyJson);
                        }
                    } else {
                        api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Sends te result of the closeAccountRequest back to the request source using a JSONRPC object.
     * @param reply Used to show which accountNumber is closed.
     */
    private static void sendCloseAccountCallback(final AccountLink reply, final ApiBean api) {
        System.out.printf("%s Successfully closed account %s\n\n\n\n", PREFIX, reply.getAccountNumber());
        Map<String, Object> result = new HashMap<>();
        api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
    }
}
