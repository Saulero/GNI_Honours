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
     * Creates a callback builder for the pinCard unblock request and then forwards the request to the Authentication
     * Service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param cookie Cookie of the User that sent the request.
     * @param requestJson Json string representing an {@link PinCard} that should be unblocked.
     */
    @RequestMapping(value = "/unblockCard", method = RequestMethod.PUT)
    public void processPinCardUnblockRequest(final Callback<String> callback,
                                          @RequestParam("request") final String requestJson,
                                          @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding pinCard unblock request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinCardUnblockExceptions(requestJson, cookie, callbackBuilder);
    }

    /**
     * Tries to verify the input of the pinCard unblock and then forward the request, send a rejection if an
     * exception is thrown.
     * @param requestJson Json string representing an {@link PinCard} that should be unblocked.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the response of the account link request back to the source of the request.
     */
    private void handlePinCardUnblockExceptions(final String requestJson, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            verifyPinCardInput(requestJson);
            doPinCardUnblockRequest(requestJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.")));
        }
    }

    /**
     * Checks if the input for an account link request is correctly formatted and contains correct values.
     * @param requestJson Json string representing an {@link PinCard} that should be unblocked.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     */
    void verifyPinCardInput(final String requestJson)
            throws IncorrectInputException, JsonSyntaxException {
        PinCard pinCard = jsonConverter.fromJson(requestJson, PinCard.class);
        final String accountNumber = pinCard.getAccountNumber();
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards a String representing a pinCard to the Authentication Service, and processes the reply if it is
     * successful or sends a rejection to the requesting source if it fails.
     * @param requestJson String representing an {@link PinCard} that should be unblocked.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doPinCardUnblockRequest(final String requestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/unblockCard", "request",
                requestJson, "cookie", cookie,
                ((httpStatusCode, httpContentType, body) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendPinCardUnblockRequestCallback(body, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                }));
    }

    /**
     * Forwards the result of a pinCard unblock request to the service that sent the request.
     * @param replyJson Json String representing the result of a pinCard unblock request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendPinCardUnblockRequestCallback(final String replyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successful account link, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
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


