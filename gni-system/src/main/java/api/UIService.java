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
//Todo move specifications to seperate file
@RequestMapping("/ui")
final class UIService {

    /** Used for json conversions. */
    private Gson jsonConverter;

    /** Prefix used when printing to indicate the message is coming from the UI Service. */
    private static final String PREFIX = "[UI]                  :";

    /**
     * Constructor.
     * @param authenticationPort port the authentication service can be found on.
     * @param authenticationHost host the authentication service can be found on.
     */
    UIService(final int authenticationPort, final String authenticationHost) {
        jsonConverter = new Gson();
    }

    /**
     * Creates a callback builder for the data request and then forwards the request to the UsersService.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param dataRequestJson A Json String representing a {@link DataRequest}.
     * @param cookie Cookie belonging to the user making the request.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   @RequestParam("request") final String dataRequestJson,
                                   @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleDataRequestExceptions(dataRequestJson, cookie, callbackBuilder);
    }

    /**
     * Handles the exceptions that occur when verifying the input of the data request, and sends a rejection
     * if the input for the request is incorrect.
     * @param dataRequestJson Json string of the data request that was received.
     * @param cookie Cookie of the user that sent the data request.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void handleDataRequestExceptions(final String dataRequestJson, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            verifyDataRequestInput(dataRequestJson);
            doDataRequest(dataRequestJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s, sending rejection.\n", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s Incorrect json syntax detected, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.", "Incorrect json syntax used.")));
        }
    }

    /**
     * Checks if the input for the data request is acceptable.
     * @param dataRequestJson Json string of the data request that was received.
     * @throws IncorrectInputException Thrown when a field does not contain an acceptable value.
     * @throws JsonSyntaxException Thrown when the Json submitted for the data request is not correct(can't be parsed).
     */
    void verifyDataRequestInput(final String dataRequestJson)
                                        throws IncorrectInputException, JsonSyntaxException {
        DataRequest dataRequest = jsonConverter.fromJson(dataRequestJson, DataRequest.class);
        RequestType requestType = dataRequest.getType();
        String accountNumber = dataRequest.getAccountNumber();

        if (requestType == null || !Arrays.asList(RequestType.values()).contains(dataRequest.getType())) {
            throw new IncorrectInputException("RequestType not correctly specified.");
        } else if (accountNumber == null && isAccountNumberRelated(dataRequest.getType())) {
            throw new IncorrectInputException("AccountNumber specified is null.");
        } else if (accountNumber != null && accountNumber.length() != accountNumberLength && isAccountNumberRelated(dataRequest.getType())) {
            throw new IncorrectInputException("AccountNumber specified is of an incorrect length.");
        }
    }

    /**
     * Returns a boolean indicating if the request type is related to a specific accountNumber.
     * @param requestType Type of request to check.
     * @return Boolean indicating if the requestType relates to an accountNumber.
     */
    private boolean isAccountNumberRelated(final RequestType requestType) {
        return requestType != RequestType.CUSTOMERDATA && requestType != RequestType.CUSTOMERACCESSLIST;
    }

    /**
     * Forwards the data request to the Authentication service and sends the reply off to processing,
     * or rejects the request if the forward fails.
     * @param dataRequestJson Json string representing a dataRequest that should be sent to the Authentication Service.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doDataRequest(final String dataRequestJson, final String cookie,
                               final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding data request.\n", PREFIX);
        authenticationClient.getAsyncWith2Params("/services/authentication/data", "request",
                                                  dataRequestJson, "cookie", cookie,
                                                  (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    processDataReply(dataReplyJson, dataRequestJson, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(dataReplyJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Checks if a data request was successful and sends the reply back to the source of the request.
     * @param dataReplyJson Body of the callback, a Json string representing a {@link DataReply}.
     * @param dataRequestJson Json string containing the {@link DataRequest} that was forwarded.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void processDataReply(final String dataReplyJson, final String dataRequestJson,
                                  final CallbackBuilder callbackBuilder) {
        DataRequest dataRequest = jsonConverter.fromJson(dataRequestJson, DataRequest.class);
        RequestType requestType = dataRequest.getType();
        switch (requestType) {
            case BALANCE:
                System.out.printf("%s Sending balance request callback.\n", PREFIX);
                callbackBuilder.build().reply(dataReplyJson);
                break;
            case TRANSACTIONHISTORY:
                System.out.printf("%s Sending transaction history request callback.\n", PREFIX);
                callbackBuilder.build().reply(dataReplyJson);
                break;
            case CUSTOMERDATA:
                System.out.printf("%s Sending customer data request callback.\n", PREFIX);
                callbackBuilder.build().reply(dataReplyJson);
                break;
            case CUSTOMERACCESSLIST:
                System.out.printf("%s Sending customer access list request callback.\n", PREFIX);
                callbackBuilder.build().reply(dataReplyJson);
                break;
            case ACCOUNTACCESSLIST:
                System.out.printf("%s Sending account access list request callback.\n", PREFIX);
                callbackBuilder.build().reply(dataReplyJson);
                break;
            default:
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Internal system error occurred.", "Incorrect requestType specified.")));
                break;
        }
    }

    /**
     * Creates a callback builder to forward the result of the request to the requester, and then forwards the request
     * to the Authentication service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param transactionRequestJson Json String representing a {@link Transaction} that is to be processed.
     * @param cookie Cookie of the User that sent the request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("request") final String transactionRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleTransactionExceptions(transactionRequestJson, cookie, callbackBuilder);
    }

    /**
     * Tries to verify the input for a transaction request, and send the correct rejection if an exception is thrown.
     * @param transactionRequestJson Json String representing a {@link Transaction} that is to be processed.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void handleTransactionExceptions(final String transactionRequestJson, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            verifyTransactionInput(transactionRequestJson);
            doTransactionRequest(transactionRequestJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s, sending rejection.\n", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.", "Syntax error when parsing json.")));
        } catch (NumberFormatException e) {
            System.out.printf("%s The transaction amount was incorrectly specified, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "The following variable was incorrectly specified: transactionAmount.")));
        }
    }

    /**
     * Checks if the input for a transaction request is correctly formatted and contains correct values.
     * @param transactionRequestJson Json string representing a transaction request.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     * @throws NumberFormatException Thrown when a string value could not be parsed to a Long.
     */
    void verifyTransactionInput(final String transactionRequestJson)
                                        throws IncorrectInputException, JsonSyntaxException, NumberFormatException {
        Transaction request = jsonConverter.fromJson(transactionRequestJson, Transaction.class);
        final String sourceAccountNumber = request.getSourceAccountNumber();
        final String destinationAccountNumber = request.getDestinationAccountNumber();
        final String destinationAccountHolderName = request.getDestinationAccountHolderName();
        final String transactionDescription = request.getDescription();
        final double transactionAmount = request.getTransactionAmount();
        if (sourceAccountNumber == null || sourceAccountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: sourceAccountNumber.");
        } else if (destinationAccountNumber == null || destinationAccountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified:"
                                                + " destinationAccountNumber.");
        } else if (destinationAccountHolderName == null || !valueHasCorrectLength(destinationAccountHolderName)) {
            throw new IncorrectInputException("The following variable was incorrectly specified:"
                                                + " destinationAccountHolderName.");
        } else if (transactionDescription == null || transactionDescription.length() > descriptionLimit
                    || transactionDescription.length() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified:"
                                                + " transactionDescription.");
        } else if (transactionAmount < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: transactionAmount.");
        } else if (request.isProcessed()) {
            throw new IncorrectInputException("The following variable was incorrectly specified: isProcessed.");
        } else if (request.isSuccessful()) {
            throw new IncorrectInputException("The following variable was incorrectly specified: isSuccessful.");
        }
    }

    /**
     * Forwards transaction request to the Authentication service and forwards the reply or sends a rejection if the
     * request fails.
     * @param transactionRequestJson Transaction request that should be processed.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final String transactionRequestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding transaction request.\n", PREFIX);
        authenticationClient.putFormAsyncWith2Params("/services/authentication/transaction", "request",
                                            transactionRequestJson, "cookie", cookie,
                                            (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendTransactionCallback(transactionReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(transactionReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Forwards the result of a transaction request to the service that sent the request.
     * @param transactionReplyJson Json String representing the executed {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionCallback(final String transactionReplyJson,
                                         final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Transaction successfully executed, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(transactionReplyJson);
    }

    /**
     * Creates a callback builder for the account link request and then forwards the request to the Authentication
     * Service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param cookie Cookie of the User that sent the request.
     * @param accountLinkRequestJson Json string representing an {@link AccountLink} that should be created in the
     *                               database.
     */
    @RequestMapping(value = "/accountLink", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback,
                                          @RequestParam("request") final String accountLinkRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account link request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkExceptions(accountLinkRequestJson, cookie, callbackBuilder);
    }

    /**
     * Tries to verify the input of the accountLink request and then forward the request, send a rejection if an
     * exception is thrown.
     * @param accountLinkRequestJson Json string representing an {@link AccountLink} that should be created
     *                               in the system.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the response of the account link request back to the source of the request.
     */
    private void handleAccountLinkExceptions(final String accountLinkRequestJson, final String cookie,
                                            final CallbackBuilder callbackBuilder) {
        try {
            verifyAccountLinkInput(accountLinkRequestJson);
            doAccountLinkRequest(accountLinkRequestJson, cookie, callbackBuilder);
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
     * @param accountLinkRequestJson Json string representing an {@link AccountLink} that should be created
     *                               in the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     */
    void verifyAccountLinkInput(final String accountLinkRequestJson)
                                        throws IncorrectInputException, JsonSyntaxException {
        AccountLink accountLink = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        final String accountNumber = accountLink.getAccountNumber();
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards a String representing an account link to the Authentication Service, and processes the reply if it is
     * successful or sends a rejection to the requesting source if it fails.
     * @param accountLinkRequestJson String representing an {@link AccountLink} that should be executed.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRequest(final String accountLinkRequestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/accountLink", "request",
                accountLinkRequestJson, "cookie", cookie,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(accountLinkReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendAccountLinkRequestCallback(accountLinkReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(accountLinkReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                }));
    }

    /**
     * Forwards the result of an account link request to the service that sent the request.
     * @param accountLinkReplyJson Json String representing the result of an account link request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendAccountLinkRequestCallback(final String accountLinkReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successful account link, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(accountLinkReplyJson);
    }

    /**
     * Checks the input of the request and then forwards it to the authentication service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkJson Json string representing an {@link AccountLink} that should be removed from the
     *                               system.
     * @param cookie Cookie of the User that sent the request.
     */
    @RequestMapping(value = "/accountLink/remove", method = RequestMethod.PUT)
    public void processAccountLinkRemoval(final Callback<String> callback,
                                          @RequestParam("request") final String accountLinkJson,
                                          @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account link removal.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkRemovalExceptions(accountLinkJson, cookie, callbackBuilder);
    }

    /**
     * Tries to parse the accountLink and verifies it contains a correct accountNumber. Then forwards the request to
     * the authenticationService.
     * @param accountLinkJson Json string representing an {@link AccountLink} that should be removed from the
     *                               system.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void handleAccountLinkRemovalExceptions(final String accountLinkJson, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            verifyAccountLinkInput(accountLinkJson);
            doAccountLinkRemoval(accountLinkJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.")));
        }
    }

    /**
     * Forwards a String representing an account link that is to be removed from the system to the Authentication
     * Service, and processes the reply if it is successful or sends a rejection to the requesting source if it fails.
     * @param accountLinkRequestJson String representing an {@link AccountLink} that should be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRemoval(final String accountLinkRequestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/accountLink/remove", "request",
                accountLinkRequestJson, "cookie", cookie,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(accountLinkReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendAccountLinkRemovalCallback(accountLinkReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(accountLinkReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                }));
    }

    /**
     * Forwards the result of an account link removal to the service that sent the request.
     * @param accountLinkReplyJson Json String representing the result of an account link removal.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendAccountLinkRemovalCallback(final String accountLinkReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successful account link removal, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(accountLinkReplyJson);
    }

    /**
     * Processes an account removal request by creating a callback builder and then calling the exception handler for
     * the request.
     * @param callback Used to send the result of the request back to the request source.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     */
    @RequestMapping(value = "/account/remove", method = RequestMethod.PUT)
    public void processAccountRemovalRequest(final Callback<String> callback,
                                             @RequestParam("accountNumber") final String accountNumber,
                                             @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountRemovalExceptions(accountNumber, cookie, callbackBuilder);
    }

    /**
     * Tries to verify the input of an account removal request and then forward the request, sends a rejection if an
     * exception occurs.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void handleAccountRemovalExceptions(final String accountNumber, final String cookie,
                                                final CallbackBuilder callbackBuilder) {
        try {
            verifyAccountRemovalInput(accountNumber);
            doAccountRemovalRequest(accountNumber, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        }
    }

    /**
     * Checks if the input for an account removal request is correctly formatted and contains correct values.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     */
    private void verifyAccountRemovalInput(final String accountNumber) throws IncorrectInputException {
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards the account removal request to the Authentication Service and sends a callback if the request is
     * successful, or sends a rejection if the request fails.
     * @param accountNumber AccountNumber of the account that is to be removed from the system.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountRemovalRequest(final String accountNumber, final String cookie,
                                         final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding account removal request.\n", PREFIX);
        authenticationClient.putFormAsyncWith2Params("/services/authentication/account/remove",
            "accountNumber", accountNumber, "cookie", cookie,
                (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    sendAccountRemovalCallback(replyJson, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(replyJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Sends the result of an account removal request to the request source.
     * @param replyJson accountNumber that was removed from the system.
     * @param callbackBuilder Used to send the result of the account removal request to the request source.
     */
    private void sendAccountRemovalCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account removal successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

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


