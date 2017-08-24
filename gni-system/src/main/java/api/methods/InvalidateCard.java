package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.PinCard;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.MAX_ACCOUNT_NUMBER_LENGTH;
import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class InvalidateCard {

    /**
     * Invalidates a pin card and creates a new one.
     * @param params Parameters of the request./
     * @param api DataBean containing everything in the ApiService
     */
    public static void invalidateCard(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Pin card replacement request received, processing...\n", PREFIX);
        try {
            String iBAN = (String) params.get("iBAN");
            String cardNumber = (String) params.get("pinCard");
            boolean newPin = false;
            if ((params.get("newPin")).equals("true")) {
                newPin = true;
            }
            verifyPinCardRemovalInput(iBAN, cardNumber);
            doPinCardReplacementRequest(params, newPin, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s\n", PREFIX, e.getMessage());
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(
                    true, 422,
                    "The user could not be authenticated, a wrong combination of credentials was provided.",
                    e.getMessage())));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(
                    true, 500, "Unknown error occurred.", "Syntax error when parsing json.")));
        }
    }

    /**
     * Checks if the variables for the replace pin card request are correctly specified and throws an exception if one
     * of the variables is not.
     * @param accountNumber AccountNumber to be checked.
     * @param cardNumber Card Number to be checked.
     * @throws IncorrectInputException Thrown when a variable is incorrectly specified(or not specified at all).
     * @throws JsonSyntaxException Thrown when the json of the pinCard object is incorrect.
     */
    private static void verifyPinCardRemovalInput(final String accountNumber, final String cardNumber)
            throws IncorrectInputException, JsonSyntaxException {
        if (accountNumber == null || accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
        if (cardNumber == null) {
            throw new IncorrectInputException("The following variable was incorrectly specified: cardNumber.");
        }
    }

    /**
     * Forwards the pin card replacement request to the authentication service.
     * @param params Parameters of the request.
     * @param newPin boolean indicating whether a new pin code should be created.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doPinCardReplacementRequest(
            final Map<String, Object> params, final boolean newPin, final ApiBean api) {
        String message = api.getJsonConverter().toJson(JSONParser.createMessageWrapper(false, 0, "Request", params));
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/invalidateCard",
                "params", message, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendPinCardReplacementCallback((PinCard) messageWrapper.getData(), newPin, api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(
                                JSONParser.createMessageWrapper(true, 500,
                                        "An unknown error occurred.",
                                        "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Sends the correct callback back to the source.
     * @param pinCard The new pinCard.
     * @param newPin boolean indicating whether a new pin code should be created.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendPinCardReplacementCallback(final PinCard pinCard, final boolean newPin, final ApiBean api) {
        System.out.printf("%s Pin card replacement successful, sending callback.\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        result.put("pinCard", "" + pinCard.getCardNumber());
        if (newPin) {
            result.put("pinCode", pinCard.getPinCode());
        }
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
