package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.PinCard;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.ApiService.accountNumberLength;
import static api.ApiService.characterLimit;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class SharedMethods {

    /**
     * Performs a new pin card request for a given account number.
     * @param accountNumber AccountNumber the new pin card should be linked to.
     * @param username Username of the user the pinCard is for.
     * @param cookie Cookie used to perform the request.
     * @param accountNrInResult Boolean indicating if the accountNumber should be in the result of the request.
     */
    public static void doNewPinCardRequest(final String accountNumber, final String username, final String cookie,
            final ApiBean api, final boolean accountNrInResult) {
        handleNewPinCardExceptions(accountNumber, cookie, username, accountNrInResult, api);
    }

    /**
     * Tries to verify the input of the request and then forward the new pin card request to the Authentication Service,
     * rejects the request if an exception occurs.
     * @param accountNumber AccountNumber the pin card should be linked to.
     * @param cookie Cookie of the user that requested the pin card.
     * @param username username of the user that owns the pincard.
     */
    private static void handleNewPinCardExceptions(final String accountNumber, final String cookie,
            final String username, final boolean accountNrInResult, final ApiBean api) {
        try {
            verifyNewPinCardInput(accountNumber);
            doNewPinCardRequest(accountNumber, cookie, username, accountNrInResult, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        }
    }

    private static void verifyNewPinCardInput(final String accountNumber) throws IncorrectInputException {
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards the new pin card request to the authentication service and forwards the result of the request to
     * the service that requested it.
     * @param accountNumber AccountNumber the pin card should be created for.
     * @param cookie Cookie of the user that sent the request.
     * @param username username of the user that owns the pincard.
     */
    private static void doNewPinCardRequest(final String accountNumber, final String cookie, final String username,
            final boolean accountNrInResult, final ApiBean api) {
        api.getAuthenticationClient().putFormAsyncWith3Params("/services/authentication/card", "accountNumber",
                accountNumber, "cookie", cookie, "username", username, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewPinCardCallback((PinCard) messageWrapper.getData(),
                                    accountNumber, accountNrInResult, api);
                        } else {
                            api.getCallbackBuilder().build().reply(body);
                        }
                    } else {
                        api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private static void sendNewPinCardCallback(final PinCard newPinCard, final String accountNumber,
            final boolean accountNrInResult, final ApiBean api) {
        System.out.printf("%s New pin card request successful.\n", PREFIX);
        if (accountNrInResult) {
            sendOpenAccountCallback(api.getCallbackBuilder(), accountNumber, newPinCard.getCardNumber(),
                    newPinCard.getPinCode(), api.getId());
        } else {
            sendAccessRequestCallback(api.getCallbackBuilder(), newPinCard.getCardNumber(),
                    newPinCard.getPinCode(), api.getId());
        }
    }



    /**
     * Creates and sends a JSONRPC response for an openAccount request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     * @param accountNumber AccountNumber of the opened account.
     * @param cardNumber CardNumber of the card created with the new account.
     * @param pinCode Pincode for the new pinCard.
     * @param id Id of the request.
     */
    private static void sendOpenAccountCallback(final CallbackBuilder callbackBuilder, final String accountNumber,
                                                final Long cardNumber, final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("iBAN", accountNumber);
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /**
     * Creates and sends a JSONRPC response for an Access request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     * @param cardNumber CardNumber of the card created with the new access link.
     * @param pinCode Pincode for the new pinCard.
     * @param id Id of the request.
     */
    private static void sendAccessRequestCallback(final CallbackBuilder callbackBuilder, final Long cardNumber,
                                                  final String pinCode, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * Checks if a the value of a field is larger than 0 and smaller than a preset character limit.
     * @param fieldValue Field to check the value length of.
     * @return Boolean indicating if the length of the string is larger than 0 and smaller than characterLimit.
     */
    public static boolean valueHasCorrectLength(final String fieldValue) {
        int valueLength = fieldValue.length();
        return valueLength > 0 && valueLength < characterLimit;
    }

    //------------------------------------------------------------------------------------------------------------------

    public static void sendErrorReply(CallbackBuilder callbackBuilder, MessageWrapper reply, final Object id) {
        JSONRPC2Response response;
        if (reply.getData() == null) {
            response = new JSONRPC2Response(new JSONRPC2Error(reply.getCode(), reply.getMessage()), id);
        } else {
            response = new JSONRPC2Response(new JSONRPC2Error(reply.getCode(), reply.getMessage(), reply.getData()), id);
        }
        callbackBuilder.build().reply(response.toJSONString());
    }
}
