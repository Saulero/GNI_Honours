package ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
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
    /** Connection to the authentication service. */
    private HttpClient authenticationClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Used to check if accountNumber are of the correct length. */
    private static int accountNumberLength = 18;
    /** Character limit used to check if a fields value is too long. */
    private static int characterLimit = 50;
    /** Character limit used to check if a transaction description is too long. */
    private static int descriptionLimit = 200;
    /** Prefix used when printing to indicate the message is coming from the UI Service. */
    private static final String PREFIX = "[UI]                  :";

    /**
     * Constructor.
     * @param authenticationPort port the authentication service can be found on.
     * @param authenticationHost host the authentication service can be found on.
     */
    UIService(final int authenticationPort, final String authenticationHost) {
        authenticationClient = httpClientBuilder().setHost(authenticationHost).setPort(authenticationPort)
                                                                              .buildAndStart();
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
            callbackBuilder.build().reject(e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s Incorrect json syntax detected, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Incorrect json syntax used.");
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
                processDataReply(dataReplyJson, dataRequestJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Data request failed.");
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
                sendBalanceRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case TRANSACTIONHISTORY:
                sendTransactionHistoryRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case CUSTOMERDATA:
                sendCustomerDataRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case CUSTOMERACCESSLIST:
                sendCustomerAccessListRequestCallback(dataReplyJson, callbackBuilder);
                break;
            case ACCOUNTACCESSLIST:
                sendAccountAccessListRequestCallback(dataReplyJson, callbackBuilder);
                break;
            default:
                callbackBuilder.build().reject("Incorrect requestType specified.");
                break;
        }
    }

    /**
     * Forwards the result of a balance request to the service that requested it.
     * @param dataReplyJson Json String containing the {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendBalanceRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending balance request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a transaction history request to the service that requested it.
     * @param dataReplyJson Json String containing the {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionHistoryRequestCallback(final String dataReplyJson,
                                                       final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending transaction history request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a customer data request to the service that requested it.
     * @param dataReplyJson Json String containing a {@link Customer}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendCustomerDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending customer data request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a customer access list request to the service that requested it.
     * @param dataReplyJson Json String containing a {@link DataReply} with the accounts.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendCustomerAccessListRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending customer access list request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of an account access list request to the service that requested it.
     * @param dataReplyJson Json String containing a {@link DataReply} with the accounts that have access
     *                      to the provided iBAN to the customer.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendAccountAccessListRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending account access list request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
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
            callbackBuilder.build().reject(e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Syntax error when parsing json.");
        } catch (NumberFormatException e) {
            System.out.printf("%s The transaction amount was incorrectly specified, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("The following variable was incorrectly specified:"
                                            + " transactionAmount.");
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
                        sendTransactionCallback(transactionReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Transaction request failed.");
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
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(transactionReplyJson));
    }

    /**
     * Handles customer creation requests by forwarding the request to the users service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newCustomerJson Json String representing a {@link Customer} that should be created.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("customer") final String newCustomerJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewCustomerExceptions(newCustomerJson, callbackBuilder);
    }

    /**
     * Tries to verify the input of a new customer request and then forward the request, sends a rejection if an
     * exception occurs.
     * @param newCustomerJson Json string representing a {@link Customer} that is to be created in the system.
     * @param callbackBuilder Used to send the result back to the source of the request.
     */
    private void handleNewCustomerExceptions(final String newCustomerJson, final CallbackBuilder callbackBuilder) {
        try {
            verifyNewCustomerInput(newCustomerJson);
            doNewCustomerRequest(newCustomerJson, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s One of the parameters has an invalid value, sending error.", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "Syntax error when parsing json.")));
        } catch (NumberFormatException e) {
            System.out.printf("%s The ssn, spendinglimit or balance was incorrectly specified, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the following variables was incorrectly specified: ssn, spendingLimit, balance.")));
        }
    }

    /**
     * Checks if the input for a new customer request is correctly formatted and contains correct values.
     * @param newCustomerJson Json string representing a {@link Customer} that is to be created in the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     * @throws NumberFormatException Thrown when a string value could not be parsed to a Long.
     */
    void verifyNewCustomerInput(final String newCustomerJson)
                                        throws IncorrectInputException, JsonSyntaxException, NumberFormatException {
        Customer newCustomer = jsonConverter.fromJson(newCustomerJson, Customer.class);
        final String initials = newCustomer.getInitials();
        final String name = newCustomer.getName();
        final String surname = newCustomer.getSurname();
        final String email = newCustomer.getEmail();
        final String telephoneNumber = newCustomer.getTelephoneNumber();
        final String address = newCustomer.getAddress();
        final String dob = newCustomer.getDob();
        final Long ssn = newCustomer.getSsn();
        final String username = newCustomer.getUsername();
        final String password = newCustomer.getPassword();
        if (initials == null || !valueHasCorrectLength(initials)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: initials.");
        } else if (name == null || !valueHasCorrectLength(name)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: name.");
        } else if (surname == null || !valueHasCorrectLength(surname)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: surname.");
        } else if (email == null || !valueHasCorrectLength(email)) {
            //todo check more formally if its actually an email address
            throw new IncorrectInputException("The following variable was incorrectly specified: email.");
        } else if (telephoneNumber == null || telephoneNumber.length() > 15 || telephoneNumber.length() < 10) {
            throw new IncorrectInputException("The following variable was incorrectly specified: telephoneNumber.");
        } else if (address == null || !valueHasCorrectLength(address)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: address.");
        } else if (dob == null || !valueHasCorrectLength(dob)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: dob.");
        } else if (ssn < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: ssn.");
        } else if (newCustomer.getAccount() == null && newCustomer.getAccount().getSpendingLimit() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: spendingLimit.");
        } else if (newCustomer.getAccount() == null && newCustomer.getAccount().getBalance() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: balance.");
        } else if (username == null || !valueHasCorrectLength(username)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: username.");
        } else if (password == null || !valueHasCorrectLength(password)) {
            //todo specify more formal password requirements
            throw new IncorrectInputException("The following variable was incorrectly specified: password.");
        }
    }

    /**
     * Checks if a the value of a field is larger than 0 and smaller than a preset character limit.
     * @param fieldValue Field to check the value length of.
     * @return Boolean indicating if the length of the string is larger than 0 and smaller than characterLimit.
     */
    private boolean valueHasCorrectLength(final String fieldValue) {
        int valueLength = fieldValue.length();
        return valueLength > 0 && valueLength < characterLimit;
    }

    /**
     * Sends the customer request to the Authentication service and then processes the reply, or sends a rejection to
     * the source of the request if the request fails..
     * @param newCustomerRequestJson Json String representing a {@link Customer} that should be created.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doNewCustomerRequest(final String newCustomerRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding customer creation request.\n", PREFIX);
        authenticationClient.putFormAsyncWith1Param("/services/authentication/customer", "customer",
                                            newCustomerRequestJson,
                                            (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(newCustomerReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewCustomerRequestCallback(newCustomerReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(newCustomerReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Forwards the created customer back to the source of the customer creation request.
     * @param newCustomerReplyJson Json String representing a {@link Customer} that was created in the system.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void sendNewCustomerRequestCallback(final String newCustomerReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Customer creation successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(newCustomerReplyJson);
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
                        callbackBuilder.build().reject("AccountLink request failed.");
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
     * Creates a callback builder for the account creation request and then forwards the request to the Authentication
     * Service.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param cookie Cookie of the User that sent the request.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account creation request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doNewAccountRequest(cookie, callbackBuilder);
    }

    /**
     * Forwards the cookie containing the customerId of the owner of the new account to the Authentication Service
     * and sends the result back to the request source, or rejects the request if the forwarding fails.
     * @param cookie Cookie of the User that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doNewAccountRequest(final String cookie, final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith1Param("/services/authentication/account/new", "cookie",
                cookie, (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(newAccountReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewAccountRequestCallback(newAccountReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(newAccountReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reject("NewAccount request failed.");
                    }
                });
    }

    /**
     * Sends the result of an account creation request to the request source.
     * @param newAccountReplyJson Json String representing a customer with a linked account that was newly created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendNewAccountRequestCallback(final String newAccountReplyJson,
                                               final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successful account creation request, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newAccountReplyJson));
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
     * Processes a login request by creating a callback builder to reach the request source and then calling the
     * exception handler for login requests.
     * @param callback Used to send the result of the request back to the request source.
     * @param authDataJson Json String representing an {@link Authentication} which contains the login information
     *                     of a User.
     */
    @RequestMapping(value = "/login", method = RequestMethod.PUT)
    public void processLoginRequest(final Callback<String> callback,
                                    @RequestParam("authData") final String authDataJson) {
        System.out.printf("%s Forwarding login request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleLoginExceptions(authDataJson, callbackBuilder);
    }

    /**
     * Tries to verify the input of the login request and then forwards the request to the Authentication Service,
     * rejects the request if an exception is thrown.
     * @param authDataJson Json String representing an {@link Authentication} which contains the login information
     *                     of a User.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleLoginExceptions(final String authDataJson, final CallbackBuilder callbackBuilder) {
        try {
            verifyLoginInput(authDataJson);
            doLoginRequest(authDataJson, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        }
    }

    /**
     * Checks if the input for a login request is correctly formatted and contains correct values.
     * @param authDataJson Json String representing {@link Authentication} information of a user trying to login.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     */
    void verifyLoginInput(final String authDataJson) throws IncorrectInputException, JsonSyntaxException {
        Authentication authentication = jsonConverter.fromJson(authDataJson, Authentication.class);
        final String username = authentication.getUsername();
        final String password = authentication.getPassword();
        final AuthenticationType authenticationType = authentication.getType();
        if (username == null || !valueHasCorrectLength(username)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: username.");
        } else if (password == null || !valueHasCorrectLength(password)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: password.");
        } else if ((authenticationType == null) || (authenticationType != AuthenticationType.LOGIN)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: authenticationType.");
        }
    }

    /**
     * Forwards the Login request to the Authentication Service and sends a callback if the request is successful, or
     * sends a rejection if the request fails.
     * @param authDataJson Json String representing {@link Authentication} information of a user trying to login.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void doLoginRequest(final String authDataJson, final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith1Param("/services/authentication/login", "authData",
                authDataJson, (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    sendLoginRequestCallback(body, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
                });
    }

    /**
     * Sends the result of a successful login request, containing a cookie that the user should use to authenticate
     * him/herself to the request source.
     * @param loginReplyJson Json String representing an {@link Authentication} object containing the
     *                       cookie the customer should use to authenticate himself in future requests.
     * @param callbackBuilder Used to send the callback to the request source.
     */
    private void sendLoginRequestCallback(final String loginReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Login successful, sending callback containing cookie.\n", PREFIX);
        callbackBuilder.build().reply(loginReplyJson);
    }

    /**
     * Creates a callbackbuilder for the request and then calls the exception handler.
     * @param callback Used to send the result of the request back to the request source.
     * @param accountNumber AccountNumber the pin card should be linked to.
     * @param cookie Cookie of the user that sent the request, this user needs to be authorized to use
     *               the accountNumber.
     * @param username The username of the user that owns the new pincard.
     */
    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void processNewPinCard(final Callback<String> callback,
                                         @RequestParam("accountNumber") final String accountNumber,
                                         @RequestParam("cookie") final String cookie,
                                         @RequestParam("username") final String username) {
        System.out.printf("%s Received new Pin card request, attempting to forward request.\n", PREFIX);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewPinCardExceptions(accountNumber, cookie, username, callbackBuilder);
    }

    /**
     * Tries to verify the input of the request and then forward the new pin card request to the Authentication Service,
     * rejects the request if an exception occurs.
     * @param accountNumber AccountNumber the pin card should be linked to.
     * @param cookie Cookie of the user that requested the pin card.
     * @param username username of the user that owns the pincard.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void handleNewPinCardExceptions(final String accountNumber, final String cookie, final String username,
                                            final CallbackBuilder callbackBuilder) {
        try {
            verifyNewPinCardInput(accountNumber);
            doNewPinCardRequest(accountNumber, cookie, username, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        }
    }

    private void verifyNewPinCardInput(final String accountNumber) throws IncorrectInputException {
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
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void doNewPinCardRequest(final String accountNumber, final String cookie, final String username,
                                     final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith3Params("/services/authentication/card", "accountNumber",
                accountNumber, "cookie", cookie, "username", username, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewPinCardCallback(body, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reject("new pin card request not successful.");
                    }
                });
    }

    private void sendNewPinCardCallback(final String jsonReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New pin card request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonReply);
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
            callbackBuilder.build().reject(e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Syntax error when parsing json.");
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
                        sendPinCardRemovalCallback(body, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Remove pin card request not successful.");
                    }
                });
    }

    private void sendPinCardRemovalCallback(final String jsonReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Pin card removal successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(jsonReply));
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


