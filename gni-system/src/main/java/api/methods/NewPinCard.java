package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;
import databeans.MethodType;
import databeans.PinCard;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.ApiService.MAX_ACCOUNT_NUMBER_LENGTH;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public abstract class NewPinCard {

    /**
     * Performs a new pin card request for a given account number.
     * @param methodType Method Type
     * @param accountNumber AccountNumber the new pin card should be linked to.
     * @param username Username of the user the pinCard is for.
     * @param cookie Cookie used to perform the request.
     * @param accountNrInResult Boolean indicating if the accountNumber should be in the result of the request.
     * @param api DataBean containing everything in the ApiService
     */
    public static void doNewPinCardRequest(
            final MethodType methodType, final String accountNumber, final String username,
            final String cookie, final ApiBean api, final boolean accountNrInResult) {
        handleNewPinCardExceptions(methodType, accountNumber, cookie, username, accountNrInResult, api);
    }

    /**
     * Tries to verify the input of the request and then forward the new pin card request to the Authentication Service,
     * rejects the request if an exception occurs.
     * @param methodType Method Type
     * @param accountNumber AccountNumber the pin card should be linked to.
     * @param cookie Cookie of the user that requested the pin card.
     * @param username username of the user that owns the pincard.
     * @param accountNrInResult determines whether this is an OpenAccount or Access request
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleNewPinCardExceptions(
            final MethodType methodType, final String accountNumber, final String cookie,
            final String username, final boolean accountNrInResult, final ApiBean api) {
        try {
            verifyNewPinCardInput(accountNumber);
            doNewPinCardRequest(methodType, accountNumber, cookie, username, accountNrInResult, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        }
    }

    /**
     * Checks the accountNumber for conformity.
     * @param accountNumber The accountNumber to test.
     * @throws IncorrectInputException Thrown if it did not conform to the specifics
     */
    private static void verifyNewPinCardInput(final String accountNumber) throws IncorrectInputException {
        if (accountNumber == null || accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards the new pin card request to the authentication service and forwards the result of the request to
     * the service that requested it.
     * @param methodType Method Type
     * @param accountNumber AccountNumber the pin card should be created for.
     * @param cookie Cookie of the user that sent the request.
     * @param username username of the user that owns the pincard.
     * @param accountNrInResult Boolean indicating if the accountNumber should be in the result of the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doNewPinCardRequest(
            final MethodType methodType, final String accountNumber, final String cookie,
            final String username, final boolean accountNrInResult, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(methodType);
        AccountLink accountLink = new AccountLink();
        accountLink.setAccountNumber(accountNumber);
        accountLink.setUsername(username);
        data.setData(accountLink);

        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/card",
                "data", api.getJsonConverter().toJson(data), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewPinCardCallback((PinCard) messageWrapper.getData(),
                                    accountNumber, accountNrInResult, api);
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
     * Sends the correct callback back to the source, depending on accountNrInResult.
     * @param newPinCard The new pinCard.
     * @param accountNumber The connected accountNumber.
     * @param accountNrInResult Boolean indicating if the accountNumber should be in the result of the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendNewPinCardCallback(final PinCard newPinCard, final String accountNumber,
            final boolean accountNrInResult, final ApiBean api) {
        System.out.printf("%s New pin card request successful.\n\n\n\n", PREFIX);
        if (accountNrInResult) {
            sendOpenAccountCallback(api.getCallbackBuilder(), accountNumber, newPinCard.getCardNumber(),
                    newPinCard.getPinCode(), newPinCard.getExpirationDate(), api.getId());
        } else {
            sendAccessRequestCallback(api.getCallbackBuilder(), newPinCard.getCardNumber(),
                    newPinCard.getPinCode(), newPinCard.getExpirationDate(), api.getId());
        }
    }



    /**
     * Creates and sends a JSONRPC response for an openAccount request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     * @param accountNumber AccountNumber of the opened account.
     * @param cardNumber CardNumber of the card created with the new account.
     * @param pinCode Pincode for the new pinCard.
     * @param expirationDate Expiration date of the pincard.
     * @param id Id of the request.
     */
    private static void sendOpenAccountCallback(final CallbackBuilder callbackBuilder, final String accountNumber,
                                                final Long cardNumber, final String pinCode,
                                                final LocalDate expirationDate, final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("iBAN", accountNumber);
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        result.put("expirationDate", expirationDate.toString());
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }

    /**
     * Creates and sends a JSONRPC response for an Access request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     * @param cardNumber CardNumber of the card created with the new access link.
     * @param pinCode Pincode for the new pinCard.
     * @param expirationDate Expiration date of the pincard.
     * @param id Id of the request.
     */
    private static void sendAccessRequestCallback(final CallbackBuilder callbackBuilder, final Long cardNumber,
                                                  final String pinCode, final LocalDate expirationDate,
                                                  final Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("pinCard", cardNumber);
        result.put("pinCode", pinCode);
        result.put("expirationDate", expirationDate.toString());
        JSONRPC2Response response = new JSONRPC2Response(result, id);
        callbackBuilder.build().reply(response.toJSONString());
    }
}
