package ui;

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
//todo needs method to add and remove pin cards.
//todo needs method to remove customer
//todo needs method to remove accountNumbers from customerAccounts.
//todo move initial ui from pin and transaction to ui
@RequestMapping("/ui")
final class UIService {
    /** Connection to the authentication service. */
    private HttpClient authenticationClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Used to check if accountNumber are of the correct length. */
    private int accountNumberLength = 18;
    /** Character limit used to check if a fields value is too long. */
    private int characterLimit = 50;
    /** Character limit used to check if a transaction description is too long. */
    private int descriptionLimit = 200;
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
     * @param dataRequestJson A Json String representing a DataRequest object {@link DataRequest}.
     * @param cookie Cookie belonging to the user making the request.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   @RequestParam("request") final String dataRequestJson,
                                   @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleDataRequestExceptions(dataRequestJson, cookie, callbackBuilder);
    }

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

    private void verifyDataRequestInput(final String dataRequestJson)
                                        throws IncorrectInputException, JsonSyntaxException {
        DataRequest dataRequest = jsonConverter.fromJson(dataRequestJson, DataRequest.class);
        RequestType requestType = dataRequest.getType();
        String accountNumber = dataRequest.getAccountNumber();
        if (requestType == null || !Arrays.asList(RequestType.values()).contains(dataRequest.getType())) {
            throw new IncorrectInputException("RequestType not correctly specified.");
        } else if (accountNumber == null || (isAccountNumberRelated(dataRequest.getType())
                                                && accountNumber.length() != accountNumberLength)) {
            throw new IncorrectInputException("AccountNumber specified is of an incorrect length.");
        }
    }

    private boolean isAccountNumberRelated(final RequestType requestType) {
        return requestType != RequestType.CUSTOMERDATA && requestType != RequestType.ACCOUNTS;
    }

    /**
     * Forwards the data request to the Users service and sends the reply off to processing, or rejects the request if
     * the forward fails.
     * @param dataRequestJson Json string representing a dataRequest that should be sent to the UsersService.
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
                callbackBuilder.build().reject("Transaction history request failed.");
            }
        });
    }

    /**
     * Checks if a data request was successfull and sends the reply back to the source of the request.
     * @param dataReplyJson Body of the callback, a Json string representing a DataReply object {@link DataReply}.
     * @param dataRequestJson Json string containing the dataRequest that was forwarded {@link DataRequest}.
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
            case ACCOUNTS:
                sendAccountsRequestCallback(dataReplyJson, callbackBuilder);
                break;
            default:
                callbackBuilder.build().reject("Incorrect requestType specified.");
                break;
        }
    }

    /**
     * Forwards the result of a balance request to the service that requested it.
     * @param dataReplyJson Json String containing the reply data {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendBalanceRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending balance request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a transaction history request to the service that requested it.
     * @param dataReplyJson Json String containing the reply data {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionHistoryRequestCallback(final String dataReplyJson,
                                                       final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending transaction history request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of a customer data request to the service that requested it.
     * @param dataReplyJson Json String containing a customer {@link Customer}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendCustomerDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending customer data request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Forwards the result of an accounts request to the service that requested it.
     * @param dataReplyJson Json String containing a data reply with the accounts belonging to a certain customer
     *                      {@link DataReply}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendAccountsRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending accounts request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }

    /**
     * Creates a callback builder to forward the result of the request to the requester, and then forwards the request
     * to the Users service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param transactionRequestJson Json String representing a Transaction object that is to be processed
     *                               {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("request") final String transactionRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleTransactionExceptions(transactionRequestJson, cookie, callbackBuilder);
    }

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

    private void verifyTransactionInput(final String transactionRequestJson)
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
        }
    }

    /**
     * Forwards transaction request to the User service and forwards the reply or sends a rejection if the request
     * fails.
     * @param transactionRequestJson Transaction request that should be processed.
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
     * @param transactionReplyJson Json String representing the executed transaction {@link Transaction}.
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
     * @param newCustomerJson Json String representing a Customer that should be created {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("customer") final String newCustomerJson) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewCustomerExceptions(newCustomerJson, callbackBuilder);
    }

    private void handleNewCustomerExceptions(final String newCustomerJson, final CallbackBuilder callbackBuilder) {
        try {
            verifyNewCustomerInput(newCustomerJson);
            doNewCustomerRequest(newCustomerJson, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Syntax error when parsing json.");
        } catch (NumberFormatException e) {
            System.out.printf("%s The ssn, spendinglimit or balance was incorrectly specified, sending rejection.\n",
                    PREFIX);
            callbackBuilder.build().reject("One of the following variables was incorrectly specified:"
                                            + " ssn, spendingLimit, balance.");
        }
    }

    private void verifyNewCustomerInput(final String newCustomerJson)
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
        final double spendingLimit = newCustomer.getAccount().getSpendingLimit();
        final double balance = newCustomer.getAccount().getBalance();
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
        } else if (spendingLimit < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: spendingLimit.");
        } else if (balance < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: balance.");
        } else if (username == null || !valueHasCorrectLength(username)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: username.");
        } else if (password == null || !valueHasCorrectLength(password)) {
            //todo specify more formal password requirements
            throw new IncorrectInputException("The following variable was incorrectly specified: password.");
        }
    }

    private boolean valueHasCorrectLength(final String fieldValue) {
        int valueLength = fieldValue.length();
        return valueLength > 0 && valueLength < characterLimit;
    }

    /**
     * Sends the customer request to the User service and then processes the reply, or sends a rejection to the
     * requester if the request fails..
     * @param newCustomerRequestJson Json String representing a Customer that should be created {@link Customer}.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doNewCustomerRequest(final String newCustomerRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding customer creation request.\n", PREFIX);
        //System.out.println(newCustomerRequestJson);
        authenticationClient.putFormAsyncWith1Param("/services/authentication/customer", "customer",
                                            newCustomerRequestJson,
                                            (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        //System.out.println("sending callback");
                        sendNewCustomerRequestCallback(newCustomerReplyJson, callbackBuilder);
                    } else {
                        System.out.println("fail: " + newCustomerReplyJson);
                        callbackBuilder.build().reject("Customer creation request failed.");
                    }
                });
    }

    /**
     * Forwards the created customer back to the service that sent the customer creation request to this service.
     * @param newCustomerReplyJson Json String representing a customer that was created in the system.
     * @param callbackBuilder Json String representing a Customer that should be created {@link Customer}.
     */
    private void sendNewCustomerRequestCallback(final String newCustomerReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Customer creation successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newCustomerReplyJson));
    }

    /**
     * Creates a callback builder for the account link request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkRequestJson Json string representing an AccountLink that should be created in the
     *                               database {@link AccountLink}.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback,
                                          @RequestParam("request") final String accountLinkRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account link request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkExceptions(accountLinkRequestJson, cookie, callbackBuilder);
    }

    private void handleAccountLinkExceptions(final String accountLinkRequestJson, final String cookie,
                                            final CallbackBuilder callbackBuilder) {
        try {
            verifyAccountLinkInput(accountLinkRequestJson);
            doAccountLinkRequest(accountLinkRequestJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Syntax error when parsing json.");
        }
    }

    private void verifyAccountLinkInput(final String accountLinkRequestJson)
                                        throws IncorrectInputException, JsonSyntaxException {
        AccountLink accountLink = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        final String accountNumber = accountLink.getAccountNumber();
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    /**
     * Forwards a String representing an account link to the Users database, and processes the reply if it is
     * successfull or sends a rejection to the requesting service if it fails.
     * @param accountLinkRequestJson String representing an account link that should be executed {@link AccountLink}.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRequest(final String accountLinkRequestJson, final String cookie,
                                      final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/account", "request",
                accountLinkRequestJson, "cookie", cookie,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendAccountLinkRequestCallback(accountLinkReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("AccountLink request failed.");
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
        System.out.printf("%s Successfull account link, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(accountLinkReplyJson));
    }

    /**
     * Creates a callback builder for the account creation request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountOwnerJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("request") final String accountOwnerJson,
                                         @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account creation request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder()
                                                                   .withStringCallback(callback);
        handleNewAccountExceptions(accountOwnerJson, cookie, callbackBuilder);
    }

    private void handleNewAccountExceptions(final String accountOwnerJson, final String cookie,
                                            final CallbackBuilder callbackBuilder) {
        try {
            verifyNewAccountInput(accountOwnerJson);
            doNewAccountRequest(accountOwnerJson, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Syntax error when parsing json.");
        }
    }

    private void verifyNewAccountInput(final String accountOwnerJson)
                                        throws IncorrectInputException, JsonSyntaxException {
        Customer accountOwner = jsonConverter.fromJson(accountOwnerJson, Customer.class);
        final String initials = accountOwner.getInitials();
        final String name = accountOwner.getName();
        final String surname = accountOwner.getSurname();
        if (initials == null || !valueHasCorrectLength(initials)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: initials.");
        } else if (name == null || !valueHasCorrectLength(name)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: name.");
        } else if (surname == null || !valueHasCorrectLength(surname)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: surname.");
        }
    }

    /**
     * Forwards the Json String representing a customer with the account to be created to the Users Service and sends
     * the result back to the requesting service, or rejects the request if the forwarding fails.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doNewAccountRequest(final String newAccountRequestJson, final String cookie,
                                     final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/account/new", "request",
                newAccountRequestJson, "cookie", cookie,
                (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendNewAccountRequestCallback(newAccountReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("NewAccount request failed.");
                    }
                });
    }

    /**
     * Sends the result of an account creation request to the service that requested it.
     * @param newAccountReplyJson Json String representing a customer with a linked account that was newly created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendNewAccountRequestCallback(final String newAccountReplyJson,
                                               final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfull account creation request, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newAccountReplyJson));
    }

    @RequestMapping(value = "/login", method = RequestMethod.PUT)
    public void processLoginRequest(final Callback<String> callback,
                                    @RequestParam("authData") final String authDataJson) {
        System.out.printf("%s Forwarding login request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doLoginRequest(authDataJson, callbackBuilder);
    }

    private void handleLoginExceptions(final String authDataJson, final CallbackBuilder callbackBuilder) {
        try {
            verifyLoginInput(authDataJson);
            doLoginRequest(authDataJson, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("Syntax error when parsing json.");
        }
    }

    private void verifyLoginInput(final String authDataJson) throws IncorrectInputException, JsonSyntaxException {
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

    private void doLoginRequest(final String authDataJson, final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith1Param("/services/authentication/login", "authData",
                authDataJson, (code, contentType, body) -> {
            if (code == HTTP_OK) {
                sendLoginRequestCallback(body, callbackBuilder);
            } else {
                //System.out.println(body);
                callbackBuilder.build().reject("Login not successfull.");
            }
                });
    }

    private void sendLoginRequestCallback(final String loginReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Login successfull, sending callback containing cookie.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(loginReplyJson));
    }

    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void processNewPinCard(final Callback<String> callback,
                                         @RequestParam("accountNumber") final String accountNumber,
                                         @RequestParam("cookie") final String cookie) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewPinCardExceptions(accountNumber, cookie, callbackBuilder);
    }

    private void handleNewPinCardExceptions(final String accountNumber, final String cookie,
                                            final CallbackBuilder callbackBuilder) {
        try {
            verifyNewPinCardInput(accountNumber);
            doNewPinCardRequest(accountNumber, cookie, callbackBuilder);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
        }
    }

    private void verifyNewPinCardInput(final String accountNumber) throws IncorrectInputException {
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }

    private void doNewPinCardRequest(final String accountNumber, final String cookie,
                                     final CallbackBuilder callbackBuilder) {
        authenticationClient.putFormAsyncWith2Params("/services/authentication/card", "accountNumber",
                accountNumber, "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        sendNewPinCardCallback(body, callbackBuilder);
                    } else {
                        //System.out.println(body);
                        callbackBuilder.build().reject("new pin card request not successfull.");
                    }
                });
    }

    private void sendNewPinCardCallback(final String jsonReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New pin card request successfull, sending callback.", PREFIX);
        callbackBuilder.build().reject(JSONParser.removeEscapeCharacters(jsonReply));
    }


}


