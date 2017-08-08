package pin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import api.IncorrectInputException;
import util.JSONParser;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * Handles Pin transactions by verifying the PIN code for the card used and
 * then handles the transaction request accordingly.
 */
@RequestMapping("/pin")
class PinService {
    /** Connection to the Transaction Dispatch Service.*/
    private HttpClient transactionDispatchClient;
    /** Connection to the Transaction Receive Service.*/
    private HttpClient transactionReceiveClient;
    /** Connection to the System Information Service.*/
    private HttpClient systemInformationClient;
    /** Database connection pool containing persistent database connections. */
    private ConnectionPool databaseConnectionPool;
    /** Used for Json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the PIN Service. */
    private static final String PREFIX = "[PIN]                 :";
    /** Used to set how long a pin card is valid */
    private static final int VALID_CARD_DURATION = 5;
    /** Used to check if a transaction without a pincode is authorized */
    private static final int CONTACTLESS_TRANSACTION_LIMIT = 25;
    /** Used to check if accountNumber are of the correct length. */
    private static int accountNumberLength = 18;

    /**
     * Service that handles all PIN and ATM related requests.
     * @param transactionDispatchPort Port the Transaction Dispatch Service can be found on.
     * @param transactionDispatchHost Host the Transaction Dispatch Service can be found on.
     * @param transactionReceivePort Port the Transaction Receive Service can be found on.
     * @param transactionReceiveHost Host the Transaction Receive Service can be found on.
     * @param systemInformationPort Port the System Information Service can be found on.
     * @param systemInformationHost Host the System Information Service can be found on.
     */
    PinService(final int transactionDispatchPort, final String transactionDispatchHost,
               final int transactionReceivePort, final String transactionReceiveHost,
               final int systemInformationPort, final String systemInformationHost) {
        transactionDispatchClient = httpClientBuilder().setHost(transactionDispatchHost)
                .setPort(transactionDispatchPort).buildAndStart();
        transactionReceiveClient = httpClientBuilder().setHost(transactionReceiveHost)
                .setPort(transactionReceivePort).buildAndStart();
        systemInformationClient = httpClientBuilder().setHost(systemInformationHost)
                .setPort(systemInformationPort).buildAndStart();
        databaseConnectionPool = new ConnectionPool();
        this.jsonConverter = new Gson();
    }

    /**
     * Creates a callbackbuilder so that the result of the request can be sent to the request source and then calls
     * the exception handler to check the pin combination and execute the transaction.
     * @param callback Used to send a reply to the request source.
     * @param pinTransactionRequestJson Json string representing a {@link PinTransaction} request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processPinTransaction(final Callback<String> callback,
                                      final @RequestParam("request") String pinTransactionRequestJson) {
        System.out.printf("%s Received new Pin request from a customer.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinExceptions(pinTransactionRequestJson, callbackBuilder);

    }

    /**
     * Checks if the request is an ATM transaction or a normal pin transaction, and then tries to authorized the
     * request. If the request is authorized it forwards the transaction, if it is not it sends a rejection.
     * @param pinTransactionRequestJson Json string representing a {@link PinTransaction} request.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void handlePinExceptions(final String pinTransactionRequestJson, final CallbackBuilder callbackBuilder) {
        try {
            PinTransaction request = jsonConverter.fromJson(pinTransactionRequestJson, PinTransaction.class);
            if (request.isATMTransaction()) {
                getATMTransactionAuthorization(request, callbackBuilder);
            } else {
                getPinTransactionAuthorization(request, callbackBuilder);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 500, "Error connecting to the pin database.")));
        } catch (IncorrectInputException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 421, e.getMessage(),
                        "A field was incorrectly specified, or not specified at all, see message.")));
        } catch (CardBlockedException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 419, e.getMessage(),
                            "The pin card used does not have the authorization to perform this request.")));
        } catch (IncorrectPinException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 421, e.getMessage(),
                            "An invalid PINcard, -code or -combination was used.")));
        } catch (CardExpiredException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 421, e.getMessage(),
                            "The pin card used is no longer valid.")));
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 500, "Unknown error occurred.",
                            "Invalid json specification.")));
        }
    }

    /**
     * Creates a new {@link Transaction} object from a {@link PinTransaction} object.
     * @param pinTransaction ATM withdrawal/deposit to convert to a {@link Transaction}.
     * @param cardAccountNumber AccountNumber of the card used in the transaction.
     * @return Transaction object representing the ATM withdrawal/deposit that is to be exectued.
     */
    Transaction createATMTransaction(final PinTransaction pinTransaction, final String cardAccountNumber) {
        String description;
        if (pinTransaction.getSourceAccountNumber().equals(cardAccountNumber)) {
            description = "ATM withdrawal card #" + pinTransaction.getCardNumber();
        } else {
            description = "ATM deposit card #" + pinTransaction.getCardNumber();
        }
        return JSONParser.createJsonTransaction(-1,
                pinTransaction.getSourceAccountNumber(), pinTransaction.getDestinationAccountNumber(),
                pinTransaction.getDestinationAccountHolderName(), description, pinTransaction.getTransactionAmount(),
                false, false);
    }

    /**
     * Fetches card information for the card used in the transaction from the database, then checks if there is money
     * taken from or written to this account, if the pincode is correct, and if the card is still valid.
     * @param pinTransaction Pin transaction that needs to be authorized.
     * @param callbackBuilder Used to send the result of the request to the requester.
     * @throws SQLException Thrown when the database cannot be reached, will cause a rejection of the transaction.
     * @throws CardBlockedException Thrown when the card used in the transaction is blocked.
     * @throws IncorrectPinException Thrown when the pincode used in the transaction is incorrect.
     * @throws CardExpiredException Thrown when the card used in the transaction is expired.
     * @throws IncorrectInputException Thrown when a field is incorrectly specified.
     */
    void getATMTransactionAuthorization(final PinTransaction pinTransaction,
                                        final CallbackBuilder callbackBuilder) throws SQLException,
            CardBlockedException, IncorrectPinException, CardExpiredException, IncorrectInputException {
        if (pinTransaction.getTransactionAmount() < 0
                || pinTransaction.getSourceAccountNumber().equals(pinTransaction.getDestinationAccountNumber())
                || pinTransaction.getSourceAccountNumber().length() != accountNumberLength
                || pinTransaction.getDestinationAccountNumber().length() != accountNumberLength) {
            throw new IncorrectInputException(
                    "The transaction amount or source/destination accountNumbers were incorrectly specified.");
        }
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCardInfo = databaseConnection.getConnection().prepareStatement(SQLStatements.getPinCard);
        getCardInfo.setLong(1, pinTransaction.getCardNumber());
        ResultSet cardInfo = getCardInfo.executeQuery();
        if (cardInfo.next()) {
            String accountNumberLinkedToCard = cardInfo.getString("account_number");
            Long incorrectAttempts = cardInfo.getLong("incorrect_attempts");
            PinCard pinCard = new PinCard(accountNumberLinkedToCard, pinTransaction.getCardNumber(),
                    cardInfo.getString("pin_code"),
                    getCustomerIdFromCardNumber(pinTransaction.getCardNumber()),
                    cardInfo.getDate("expiration_date").toLocalDate());
            if (accountNumberLinkedToCard.equals(pinTransaction.getDestinationAccountNumber())
                    || accountNumberLinkedToCard.equals(pinTransaction.getSourceAccountNumber())) {
                if (!pinTransaction.getSourceAccountNumber().equals(pinTransaction.getDestinationAccountNumber())) {
                    if (incorrectAttempts < 3) {
                        checkPinValidity(pinCard, pinTransaction, true, callbackBuilder);
                    }  else {
                        throw new CardBlockedException("The card used is blocked.");
                    }
                } else {
                    getCardInfo.close();
                    databaseConnectionPool.returnConnection(databaseConnection);
                    throw new IncorrectInputException(
                            "The source and destination accountNumbers are not allowed to be equal.");
                }
            } else {
                getCardInfo.close();
                databaseConnectionPool.returnConnection(databaseConnection);
                throw new IncorrectPinException(
                        "The pin card used did not belong to one of the accountNumbers used in the transaction.");
            }
        } else {
            getCardInfo.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            incrementIncorrectAttempts(pinTransaction.getCardNumber());
            throw new IncorrectPinException("Pin card does not exist.");
        }
        getCardInfo.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Checks if the pin card used is not blocked or expired and if the pinCode used is correct.
     * @param callbackBuilder Used to send rejections to the requester if the request fails.
     * @param pinTransaction Transaction that this card is used for.
     * @throws SQLException Thrown when a database error occurs.
     * @throws IncorrectInputException Thrown when the card cannot be unblocked due to an incorrect input.
     */
    private void checkPinValidity(final PinCard pinCard, final PinTransaction pinTransaction, final boolean isATM,
                                  final CallbackBuilder callbackBuilder) throws SQLException, IncorrectInputException {
        systemInformationClient.getAsync("/services/systemInfo/date",
            (httpStatusCode, contentType, body) -> {
                if (httpStatusCode == HTTP_OK) {
                    MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser
                            .removeEscapeCharacters(body), MessageWrapper.class);
                    if (!messageWrapper.isError()) {
                        LocalDate systemDate = (LocalDate) messageWrapper.getData();
                        if (pinCard.getExpirationDate().isAfter(systemDate)) {
                            try {
                                if (pinCard.getPinCode().equals(pinTransaction.getPinCode())) {
                                    try {
                                        // reset the count
                                        unblockPinCard(new PinCard(pinCard.getAccountNumber(),
                                                        pinCard.getCardNumber()));
                                    } catch (NoEffectException e) {
                                        // do nothing
                                    }
                                    if (pinCard.getAccountNumber().equals(pinTransaction.getSourceAccountNumber())) {
                                        if (isATM) {
                                            Transaction transaction = createATMTransaction(pinTransaction,
                                                    pinCard.getAccountNumber());
                                            doTransactionRequest(transaction, pinCard.getCustomerId(), callbackBuilder);
                                        } else {
                                            Transaction transaction = JSONParser.createJsonTransaction(-1,
                                                    pinTransaction.getSourceAccountNumber(),
                                                    pinTransaction.getDestinationAccountNumber(),
                                                    pinTransaction.getDestinationAccountHolderName(),
                                                    "PIN Transaction card #" + pinTransaction.getCardNumber(),
                                                    pinTransaction.getTransactionAmount(), false,
                                                    false);
                                            doTransactionRequest(transaction, pinCard.getCustomerId(), callbackBuilder);
                                        }
                                    } else {
                                        Transaction transaction = createATMTransaction(pinTransaction,
                                                pinCard.getAccountNumber());
                                        doDepositTransactionRequest(transaction, callbackBuilder);
                                    }
                                } else {
                                    incrementIncorrectAttempts(pinTransaction.getCardNumber());
                                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                            .createMessageWrapper(true, 421,
                                                    "The pin code used is incorrect.",
                                                    "An invalid PINcard, -code or -combination was used.")));
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                        .createMessageWrapper(true, 500,
                                                "Error connecting to the pin database.")));
                            } catch (IncorrectInputException e) {
                                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                        .createMessageWrapper(true, 421, e.getMessage(),
                                        "A field was incorrectly specified,"
                                                + " or not specified at all, see message.")));
                            }
                        } else {
                            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                    .createMessageWrapper(true, 421,
                                            "The card used is expired.",
                                            "The pin card used is no longer valid.")));
                        }
                    } else {
                        callbackBuilder.build().reply(body);
                    }
                } else {
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                            .createMessageWrapper(true, 500,
                                    "An unknown error occurred.",
                                    "There was a problem with one of the HTTP requests")));
                }
            });
    }

    /**
     * Increments the amount of incorrect pin code attempts in the database.
     * @param cardNumber Cardnumber to increment the amount of incorrect attempts for.
     * @throws SQLException Thrown when there is an error connecting to the database.
     */
    private void incrementIncorrectAttempts(final Long cardNumber) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement incrementStatement = databaseConnection.getConnection()
                                                        .prepareStatement(SQLStatements.incrementIncorrectAttempts);
        incrementStatement.setLong(1, cardNumber);
        incrementStatement.execute();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Fetches the customerId of the customer the card belongs to if the card number and pin code are correct and
     * belong to a customer, otherwise throws an IncorrectPinException.
     * @param cardNumber Card number of the card used.
     * @return CustomerId of the owner of the card.
     * @throws SQLException Thrown when a database issue occurs.
     * @throws IncorrectInputException Thrown when the cardNumber does not return a pin card.
     */
    Long getCustomerIdFromCardNumber(final Long cardNumber) throws SQLException, IncorrectInputException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCustomerId = databaseConnection.getConnection().prepareStatement(SQLStatements
                                                                                .getCustomerIdFromCardNumber);
        getCustomerId.setLong(1, cardNumber);
        ResultSet fetchedCustomerId = getCustomerId.executeQuery();
        if (fetchedCustomerId.next()) {
            Long customerId = fetchedCustomerId.getLong(1);
            getCustomerId.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return customerId;
        } else {
            getCustomerId.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            throw new IncorrectInputException("There does not exist a customer with this cardnumber.");
        }
    }

    /**
     * Checks if the source account number of the transaction matches the account number of the card that was used and
     * if the pin code for the card was correct/the card is not expired.
     * @param pinTransaction PinTransaction that should be authorized.
     * @throws SQLException Thrown when the database cannot be reached, will cause a rejection of the transaction.
     * @throws CardBlockedException Thrown when the card used in the transaction is blocked.
     * @throws IncorrectPinException Thrown when the pincode used in the transaction is incorrect.
     * @throws CardExpiredException Thrown when the card used in the transaction is expired.
     * @throws IncorrectInputException Thrown when a field is incorrectly specified.
     */
    void getPinTransactionAuthorization(final PinTransaction pinTransaction,
                                        final CallbackBuilder callbackBuilder) throws SQLException,
                                        CardExpiredException, IncorrectInputException, IncorrectPinException,
                                        CardBlockedException {
        if (pinTransaction.getTransactionAmount() < 0
                || pinTransaction.getSourceAccountNumber().equals(pinTransaction.getDestinationAccountNumber())
                || pinTransaction.getSourceAccountNumber().length() != accountNumberLength
                || pinTransaction.getDestinationAccountNumber().length() != accountNumberLength) {
            throw new IncorrectInputException(
                    "The transaction amount or source/destination accountNumbers were incorrectly specified.");
        }
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCardInfo = databaseConnection.getConnection()
                                                    .prepareStatement(SQLStatements.getPinCard);
        getCardInfo.setLong(1, pinTransaction.getCardNumber());
        ResultSet cardInfo = getCardInfo.executeQuery();
        if (cardInfo.next()) {
            final String pinCodeUsed = pinTransaction.getPinCode();
            String accountNumberLinkedToCard = cardInfo.getString("account_number");
            Long incorrectAttempts = cardInfo.getLong("incorrect_attempts");
            PinCard pinCard = new PinCard(accountNumberLinkedToCard, pinTransaction.getCardNumber(),
                    cardInfo.getString("pin_code"),
                    getCustomerIdFromCardNumber(pinTransaction.getCardNumber()),
                    cardInfo.getDate("expiration_date").toLocalDate());
            if (pinTransaction.getSourceAccountNumber().equals(accountNumberLinkedToCard)) {
                if (incorrectAttempts < 3) {
                    if (pinCodeUsed == null) {
                        if (pinTransaction.getTransactionAmount() < CONTACTLESS_TRANSACTION_LIMIT) {
                            checkPinValidity(pinCard, pinTransaction, false, callbackBuilder);
                        } else {
                            getCardInfo.close();
                            databaseConnectionPool.returnConnection(databaseConnection);
                            throw new IncorrectPinException("The transfer amount is too high, please enter pin code.");
                        }
                    } else if (pinCodeUsed.equals(pinCard.getPinCode())) {
                        checkPinValidity(pinCard, pinTransaction, false, callbackBuilder);
                    } else {
                        getCardInfo.close();
                        databaseConnectionPool.returnConnection(databaseConnection);
                        incrementIncorrectAttempts(pinCard.getCardNumber());
                        throw new IncorrectPinException("The pin code used was incorrect.");
                    }
                } else {
                    getCardInfo.close();
                    databaseConnectionPool.returnConnection(databaseConnection);
                    throw new CardBlockedException("The card used is blocked.");
                }
            } else {
                getCardInfo.close();
                databaseConnectionPool.returnConnection(databaseConnection);
                incrementIncorrectAttempts(pinTransaction.getCardNumber());
                throw new IncorrectPinException("Pin card does not belong to accountNumber used in the transaction.");
            }
        } else {
            getCardInfo.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            incrementIncorrectAttempts(pinTransaction.getCardNumber());
            throw new IncorrectPinException("Pin card does not exist.");
        }
        getCardInfo.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Fetches the accountNumber linked to a card from the pin database.
     * @param cardNumber CardNumber to fetch the linked accountNumber for.
     * @return AccountNumber linked to the card with given cardNumber.
     * @throws SQLException Thrown when the datbase cannot be reached, will cause a rejection of the transaction the
     * request is for.
     * @throws IncorrectPinException Thrown when there is no accountNumber for the given cardNumber in the database,
     * will cause a rejection of the transaction the request is for.
     */
    String getAccountNumberWithCardNumber(final Long cardNumber) throws SQLException, IncorrectPinException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getAccountNumber = databaseConnection.getConnection()
                                                    .prepareStatement(SQLStatements.getAccountNumberUsingCardNumber);
        getAccountNumber.setLong(1, cardNumber);
        ResultSet accountNumberResult = getAccountNumber.executeQuery();
        if (accountNumberResult.next()) {
            return accountNumberResult.getString("account_number");
        } else {
            throw new IncorrectPinException("There does not exist an accountNumber for this pin card in the database.");
        }
    }



    /**
     * Sends the Transaction to the transactionDispatchClient and handles the reply when it is received by checking
     * if the request was successful, and sending it off for processing if it was, or sending a rejection to the
     * request source of the request failed.
     * @param request Transaction that should be processed.
     * @param customerId CustomerId of the customer that requested the transaction.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void doTransactionRequest(final Transaction request, final Long customerId,
                                      final CallbackBuilder callbackBuilder) {
        transactionDispatchClient.putFormAsyncWith2Params("/services/transactionDispatch/transaction",
                "request", jsonConverter.toJson(request), "customerId", customerId,
        (code, contentType, replyBody) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    Transaction reply = (Transaction) messageWrapper.getData();
                    processTransactionReply(reply, request, replyBody, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(replyBody);
                }
            } else {
                System.out.printf("%s Transaction request failed, sending rejection.\n", PREFIX);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Sends the deposit request to the transaction receive client and handles the the reply when it is received by
     * checking if the request was successful, and sending it off for processing if it was, or sending a rejection
     * to the request source of the request failed.
     * @param request Transaction that should be processed.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void doDepositTransactionRequest(final Transaction request, final CallbackBuilder callbackBuilder) {
        transactionReceiveClient.putFormAsyncWith1Param("/services/transactionReceive/transaction",
                "request", jsonConverter.toJson(request), ((code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    Transaction reply = (Transaction) messageWrapper.getData();
                    processTransactionReply(reply, request, body, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        }));
    }

    /**
     * Processes a transaction reply by checking if it was successful, and the request that was executed matches the
     * request that was sent and sends the matching callback to the request source.
     * @param reply Transaction reply for the transaction request that was made.
     * @param request Transaction request that was sent to the Transaction Dispatch service.
     * @param replyJson Original message
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void processTransactionReply(final Transaction reply, final Transaction request, final String replyJson,
                                         final CallbackBuilder callbackBuilder) {
        if (reply.equalsRequest(request)) {
            if (reply.isSuccessful()) {
                System.out.printf("%s Pin transaction was successful, sending callback.\n", PREFIX);
                callbackBuilder.build().reply(replyJson);
            }
        } else {
            System.out.printf("%s Pin transaction was unsuccessful, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                                                "Unknown error occurred.")));
        }
    }

    /**
     * Creates a callbackbuilder so the result of the new pin card request can be sent to the request source and then
     * calls the correct exception handler to execute the request.
     * @param callback Used to send the result of the request to the request source.
     * @param requesterId CustomerId of the user that sent the request.
     * @param ownerId CustomerId of the customer that wants a new pin card.
     * @param accountNumber AccountNumber the pin card should be created for.
     */
    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void addNewPinCard(final Callback<String> callback, final @RequestParam("requesterId") String requesterId,
                              final @RequestParam("ownerId") String ownerId,
                              final @RequestParam("accountNumber") String accountNumber) {
        System.out.printf("%s Received new pin card request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        generateExpirationDate(requesterId, ownerId, accountNumber, callbackBuilder);
    }

    /**
     * Generates an expiration date for a pin card by adding the valid card duration to the current date.
     * @param requesterId CustomerId of the user that sent the request.
     * @param ownerId CustomerId of the customer that will own the new card.
     * @param accountNumber AccountNumber the card should be created for.
     * @param callbackBuilder Used to send the creation result to the request source.
     */
    void generateExpirationDate(final String requesterId, final String ownerId, final String accountNumber,
                                final CallbackBuilder callbackBuilder) {
        systemInformationClient.getAsync("/services/systemInfo/date", (httpStatusCode, contentType, body) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                        MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    LocalDate systemDate = (LocalDate) messageWrapper.getData();
                    LocalDate expirationDate = systemDate.plusYears(VALID_CARD_DURATION);
                    handleNewPinCardExceptions(expirationDate, requesterId, ownerId, accountNumber, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                        500, "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Creates a new pin card for the Customer with customerId and accountNumber if the creation succeeds sends the
     * result back to the request source, otherwise rejects the request.
     * @param expirationDate ExpirationDate for the new pin card.
     * @param requesterId CustomerId of the user that sent the request.
     * @param ownerId CustomerId of the customer that will own the new card.
     * @param accountNumber AccountNumber the card should be created for.
     * @param callbackBuilder Used to send the creation result to the request source.
     */
    private void handleNewPinCardExceptions(final LocalDate expirationDate, final String requesterId,
                                            final String ownerId, final String accountNumber,
                                            final CallbackBuilder callbackBuilder) {
        try {
            Long cardNumber = getNextAvailableCardNumber();
            String pinCode = generatePinCode();
            //todo check if the requester has permissions for the card.
            PinCard pinCard;
            if (ownerId.length() > 0) {
                pinCard = JSONParser.createJsonPinCard(accountNumber, cardNumber, pinCode,
                        Long.parseLong(ownerId), expirationDate);
            } else {
                pinCard = JSONParser.createJsonPinCard(accountNumber, cardNumber, pinCode,
                        Long.parseLong(requesterId), expirationDate);
            }
            addPinCardToDatabase(pinCard);
            sendNewPinCardCallback(pinCard, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to pin database.")));
        } catch (NumberFormatException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "Something went wrong when parsing the customerId in Pin.")));
        } catch (NoSuchAlgorithmException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.")));
        }
    }

    /**
     * Fetches the next available card number by selecting the highest cardNumber from the database and adding 1 to it.
     * @return First available card number that should be used to create a pin card.
     * @throws SQLException Thrown when the datbase cant be reached, will cause a new card request to be rejected.
     */
    Long getNextAvailableCardNumber() throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getHighestCardNumber = databaseConnection.getConnection()
                                                            .prepareStatement(SQLStatements.getHighestCardNumber);
        ResultSet highestCardNumber = getHighestCardNumber.executeQuery();
        if (highestCardNumber.next()) {
            Long cardNumber = highestCardNumber.getLong(1);
            getHighestCardNumber.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return cardNumber + 1;
        } else {
            //There are no cards in the system
            getHighestCardNumber.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return 0L;
        }
    }

    /**
     * Generates a random pin code for a new pin card.
     * @return Pincode to be used for a new pin card.
     * @throws NoSuchAlgorithmException Thrown when the algorithm cannot be found, will cause a new pin card request
     * to be rejected.
     */
    private String generatePinCode() throws NoSuchAlgorithmException {
        SecureRandom randomGenerator = SecureRandom.getInstance("SHA1PRNG");
        return String.format("%04d", randomGenerator.nextInt(9999));
    }

    /**
     * Inserts a pin card into the pin database.
     * ExpirationDate for the card will be set to the day that the date is on, time is disregarded once the card is
     * in the database.
     * @param pinCard Pincard to be inserted into the database.
     * @throws SQLException Thrown when the insertion fails, will reject the new pin card request.
     */
    void addPinCardToDatabase(final PinCard pinCard) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement addPinCard = databaseConnection.getConnection()
                                                         .prepareStatement(SQLStatements.addPinCard);
        addPinCard.setString(1, pinCard.getAccountNumber());
        addPinCard.setLong(2, pinCard.getCustomerId());
        addPinCard.setLong(3, pinCard.getCardNumber());
        addPinCard.setString(4, pinCard.getPinCode());
        addPinCard.setDate(5, java.sql.Date.valueOf(pinCard.getExpirationDate()));
        addPinCard.setLong(6, 0L);
        addPinCard.executeUpdate();
        addPinCard.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }


    private void sendNewPinCardCallback(final PinCard pinCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully created pin card, card #%s, accountno. %s  sending callback\n", PREFIX, pinCard.getCardNumber(), pinCard.getAccountNumber());
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", pinCard)));
    }

    /**
     * Removes all pin cards linked to the account with accountNumber from the system.
     * @param callback Used to send the result of the removal to the request source.
     * @param accountNumber AccountNumber for which all pin cards should be removed.
     */
    @RequestMapping(value = "/account/remove", method = RequestMethod.PUT)
    public void removeAccountCards(final Callback<String> callback,
                                   final @RequestParam("accountNumber") String accountNumber) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleRemoveAccountCardsExceptions(accountNumber, callbackBuilder);
    }

    private void handleRemoveAccountCardsExceptions(final String accountNumber, final CallbackBuilder callbackBuilder) {
        try {
            deleteAccountCardsFromDatabase(accountNumber);
            sendRemoveAccountCardsCallback(accountNumber, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to the pin database.")));
        }
    }

    private void deleteAccountCardsFromDatabase(final String accountNumber) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement removeAccountCards = databaseConnection.getConnection()
                                                                .prepareStatement(SQLStatements.removeAccountCards);
        removeAccountCards.setString(1, accountNumber);
        removeAccountCards.execute();
        databaseConnection.close();
    }

    private void sendRemoveAccountCardsCallback(final String accountNumber, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s All pin cards for account with accountNumber %s successfully deleted from the system,"
                            + " sending callback.\n", PREFIX, accountNumber);
        AccountLink reply = new AccountLink(0L, accountNumber);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", reply)));
    }

    /**
     * Creates a callbackbuilder to send the result of the request to and then calls the exception handler to execute
     * the pin card unblock. Sends a callback if the removal is successful or a rejection if the removal fails.
     * @param callback Used to send the result of the request to the request source.
     * @param pinCardJson Json String representing a {@link PinCard} that should be unblocked.
     */
    @RequestMapping(value = "/unblockCard", method = RequestMethod.PUT)
    public void unblockCard(final Callback<String> callback, final @RequestParam("pinCard") String pinCardJson) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinCardUnblockExceptions(pinCardJson, callbackBuilder);
    }

    /**
     * Tries to create a {@link PinCard} from the Json string and then unblock it. Sends a rejection
     * if this fails or a callback with the {@link PinCard} that was unblocked if it is successful.
     * @param pinCardJson Json String representing a {@link PinCard} that should be unblocked.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handlePinCardUnblockExceptions(final String pinCardJson, final CallbackBuilder callbackBuilder) {
        try {
            PinCard pinCard = jsonConverter.fromJson(pinCardJson, PinCard.class);
            unblockPinCard(pinCard);
            sendPinCardUnblockCallback(pinCard, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to the Pin database.")));
        } catch (IncorrectInputException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
        } catch (NoEffectException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 420, "The action has no real effect.", e.getMessage())));
        }
    }

    /**
     * Unblocks a PinCard in the pin database.
     * @param pinCard Pin card that should be unblocked.
     * @throws SQLException Thrown when the sql query fails, will cause the unblock request to be rejected.
     * @throws NumberFormatException Cause when a parameter is incorrectly specified, will cause the unblock request
     * to be rejected.
     */
    void unblockPinCard(final PinCard pinCard) throws SQLException, IncorrectInputException, NoEffectException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection().prepareStatement(SQLStatements.getPinCard);
        ps.setLong(1, pinCard.getCardNumber());
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            String accountNumber = rs.getString("account_number");
            Long attempts = rs.getLong("incorrect_attempts");
            rs.close();

            if (!pinCard.getAccountNumber().equals(accountNumber)) {
                throw new IncorrectInputException("The provided account number does not match the account number in the system.");
            } else {
                // reset the count
                ps = con.getConnection().prepareStatement(SQLStatements.unblockPinCard);
                ps.setLong(1, pinCard.getCardNumber());
                ps.executeUpdate();

                if (attempts < 3) {
                    throw new NoEffectException("The card was not blocked in the first place, but the attempts count has been reset none the less");
                }
            }
        } else {
            rs.close();
            throw new IncorrectInputException("The provided pin card does not appear to exist.");
        }

        ps.close();
        con.close();
        databaseConnectionPool.returnConnection(con);
    }

    private void sendPinCardUnblockCallback(final PinCard pinCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Pin card #%s successfully unblocked, sending callback.\n", PREFIX, pinCard.getCardNumber());
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", pinCard)));
    }

    /**
     * Safely shuts down the PinService.
     */
    void shutdown() {
        if (transactionReceiveClient != null) {
            transactionReceiveClient.stop();
        }
        if (transactionDispatchClient != null) {
            transactionDispatchClient.stop();
        }
        if (databaseConnectionPool != null) {
            databaseConnectionPool.close();
        }
    }
}
