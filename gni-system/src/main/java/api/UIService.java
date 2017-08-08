package api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import databeans.*;
import io.advantageous.qbit.annotation.*;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.util.Arrays;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 2
 * Interface that outside users can use to view their balance, transaction history, customer information, create
 * new accounts and make transactions.
 */
final class UIService {


    /**
     * Creates a callbackBuilder for the request so that the result can be sent back to the request source and then
     * calls the exception handler for the request.
     * @param callback Used to send the result of the request back to the request source.
     * @param pinCardJson Json String representing a {@link PinCard} that is to be removed from the system.
     * @param cookie Cookie of the user that sent the request.
     */
    @RequestMapping(value = "/card/remove", method = RequestMethod.PUT)
    public void processPinCardRemoval(final Callback<String> callback,
                                      @RequestParam("pinCard") final String pinCardJson,
                                      @RequestParam("cookie") final String cookie) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinCardRemovalExceptions(pinCardJson, cookie, callbackBuilder);
    }

    /**
     * Tries to verify the input of the request and then forward the pin card removal request to the Authentication
     * Service, rejects the request if an exception occurs.
     * @param pinCardJson Json String representing a {@link PinCard} that is to be removed from the system.
     * @param cookie Cookie of the user that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void handlePinCardRemovalExceptions(final String pinCardJson, final String cookie,
                                                final CallbackBuilder callbackBuilder) {
        try {
            verifyPinCardRemovalInput(pinCardJson);
            doPinCardRemovalRequest(pinCardJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 422, "The user could not be authenticated, a wrong combination of credentials was provided.", e.getMessage())));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.", "Syntax error when parsing json.")));
        }
    }

    /**
     * Checks if the variables for the remove pin card request are correctly specified and throws an exception if one
     * of the variables is not.
     * @param pinCardJson Json String representing a pin card that should be removed from the system.
     * @throws IncorrectInputException Thrown when a variable is incorrectly specified(or not specified at all).
     * @throws JsonSyntaxException Thrown when the json of the pinCard object is incorrect.
     */
    void verifyPinCardRemovalInput(final String pinCardJson) throws IncorrectInputException,
            JsonSyntaxException {
        PinCard pinCard = jsonConverter.fromJson(pinCardJson, PinCard.class);
        String accountNumber = pinCard.getAccountNumber();
        Long cardNumber = pinCard.getCardNumber();
        String pinCode = pinCard.getPinCode();
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
        if (cardNumber == null) {
            throw new IncorrectInputException("The following variable was incorrectly specified: cardNumber.");
        }
        if (pinCode == null) {
            throw new IncorrectInputException("The following variable was incorrectly specified: pinCode.");
        }
    }

    /**
     * Forwards the pin card removal request to the authentication service, forwards the result to the request source
     * if the request is successful, or sends a rejection if it is not.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     * @param cookie Cookie of the user that sent the request.
     * @param callbackBuilder Used to forward the result of the request to the request source.
     */
    private void doPinCardRemovalRequest(final String pinCardJson, final String cookie,
                                         final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/card/remove",
                "pinCard", pinCardJson, "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendPinCardRemovalCallback(body, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendPinCardRemovalCallback(final String jsonReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Pin card removal successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonReply);
    }

    /**
     * Safely shuts down the PinService.
     */
    void shutdown() {
        if (authenticationClient != null) {
            authenticationClient.stop();
        }
    }
}


