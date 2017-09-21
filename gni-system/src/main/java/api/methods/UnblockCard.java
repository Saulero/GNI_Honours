package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.MethodType;
import databeans.PinCard;
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
public abstract class UnblockCard {

    /**
     * Unblocks a blocked pin card, requires logging in.
     * @param params Parameters of the request(authToken, iBAN, pinCard).
     * @param api DataBean containing everything in the ApiService
     */
    public static void unblockCard(final Map<String, Object> params, final ApiBean api) {
        String accountNumber = (String) params.get("iBAN");
        String pinCard = (String) params.get("pinCard");
        String cookie = (String) params.get("authToken");
        PinCard request = new PinCard(accountNumber, Long.parseLong(pinCard));
        System.out.printf("%s Sending pinCard unblock request.\n", PREFIX);
        handlePinCardUnblockExceptions(request, cookie, api);
    }

    /**
     * Tries to verify the input of the pinCard unblock and then forward the request, send a rejection if an
     * exception is thrown.
     * @param pinCard {@link PinCard} that should be unblocked.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void handlePinCardUnblockExceptions(final PinCard pinCard, final String cookie, final ApiBean api) {
        try {
            verifyPinCardInput(pinCard);
            doPinCardUnblockRequest(pinCard, cookie, api);
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
     * Checks if the input for an account link request is correctly formatted and contains correct values.
     * @param pinCard {@link PinCard} that should be unblocked.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     */
    private static void verifyPinCardInput(final PinCard pinCard)
            throws IncorrectInputException, JsonSyntaxException {
        final String accountNumber = pinCard.getAccountNumber();
        if (accountNumber == null || accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards a String representing a pinCard to the Authentication Service, and processes the reply if it is
     * successful or sends a rejection to the requesting source if it fails.
     * @param pinCard {@link PinCard} that should be unblocked.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doPinCardUnblockRequest(final PinCard pinCard, final String cookie, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.UNBLOCK_CARD);
        data.setData(pinCard);

        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/unblockCard",
                "data", api.getJsonConverter().toJson(data), ((httpStatusCode, httpContentType, body) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendPinCardUnblockRequestCallback((PinCard) messageWrapper.getData(), api);
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
     * Forwards the result of a pinCard unblock request to the service that sent the request.
     * @param pinCard The result of a pinCard unblock request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendPinCardUnblockRequestCallback(final PinCard pinCard, final ApiBean api) {
        System.out.printf("%s PinCard unblocked successfully for AccountNumber: %s, CardNumber: %s\n\n\n\n",
                PREFIX, pinCard.getAccountNumber(), pinCard.getCardNumber());
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
