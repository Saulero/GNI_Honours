package pin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;
import databeans.PinCard;
import databeans.PinTransaction;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import databeans.Transaction;
import util.JSONParser;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;

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
    /** Connection to the Transaction Dispatch service.*/
    private HttpClient transactionDispatchClient;
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

    PinService(final int transactionDispatchPort, final String transactionDispatchHost) {
        transactionDispatchClient = httpClientBuilder().setHost(transactionDispatchHost)
                                                        .setPort(transactionDispatchPort).buildAndStart();
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
     * Checks if the pin code matches the card number, and fetches the customerId of the owner of the card, then
     * forwards the transaction request to the TransactionDispatch service.
     * @param pinTransactionRequestJson Json string representing a {@link PinTransaction} request.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void handlePinExceptions(final String pinTransactionRequestJson, final CallbackBuilder callbackBuilder) {
        try {
            PinTransaction request = jsonConverter.fromJson(pinTransactionRequestJson, PinTransaction.class);
            Long customerId = getCustomerIdFromCardNumber(request.getCardNumber());
            if (request.isATMTransaction()) {
                if (getATMTransactionAuthorization(request)) {
                    Transaction transaction = createATMTransaction(request);
                    doTransactionRequest(transaction, customerId, callbackBuilder);
                } else {
                    callbackBuilder.build().reject("Unauthorized ATM request.");
                }
            } else {
                if (getPinTransactionAuthorization(request)) {
                    Transaction transaction = JSONParser.createJsonTransaction(-1,
                            request.getSourceAccountNumber(), request.getDestinationAccountNumber(),
                            request.getDestinationAccountHolderName(),
                            "PIN Transaction card #" + request.getCardNumber(),
                            request.getTransactionAmount(), false, false);
                    doTransactionRequest(transaction, customerId, callbackBuilder);
                } else {
                    callbackBuilder.build().reject("Unauthorized Pin request.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("Something went wrong when connecting to the pin database.");
        } catch (IncorrectPinException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("Incorrect PIN/CardNumber.");
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("Invalid json specification.");
        }
    }

    private Transaction createATMTransaction(final PinTransaction pinTransaction) throws IncorrectPinException,
                                                                                    SQLException {
        String cardAccountNumber = getAccountNumberWithCardNumber(pinTransaction.getCardNumber());
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

    private boolean getATMTransactionAuthorization(final PinTransaction pinTransaction) throws SQLException {
        boolean authorized = false;
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCardInfo = databaseConnection.getConnection()
                .prepareStatement(SQLStatements.getPinCard);
        getCardInfo.setLong(1, pinTransaction.getCardNumber());
        ResultSet cardInfo = getCardInfo.executeQuery();
        if (cardInfo.next()) {
            String accountNumberLinkedToCard = cardInfo.getString("account_number");
            if (accountNumberLinkedToCard.equals(pinTransaction.getDestinationAccountNumber())
                    || accountNumberLinkedToCard.equals(pinTransaction.getSourceAccountNumber())) {
                if (cardInfo.getString("pin_code").equals(pinTransaction.getPinCode())) {
                    authorized = true;
                }
            }
        }
        getCardInfo.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return authorized;
    }

    /**
     * Fetches the customerId of the customer the card belongs to if the card number and pin code are correct and
     * belong to a customer, otherwise throws an IncorrectPinException.
     * @param cardNumber Card number of the card used.
     * @return CustomerId of the owner of the card.
     * @throws SQLException Thrown when a database issue occurs.
     * @throws IncorrectPinException Thrown when the cardNumber and pinCode don't match.
     */
    private Long getCustomerIdFromCardNumber(final Long cardNumber) throws SQLException, IncorrectPinException {
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
            throw new IncorrectPinException("There does not exist a customer with this cardnumber.");
        }
    }

    /**
     * Checks if the source account number of the transaction matches the account number of the card that was used and
     * if the pin code for the card was correct/the card is not expired.
     * @param transaction PinTransaction that should be authorized.
     * @return Boolean indicating if the request is authorized and should be executed.
     * @throws SQLException Thrown when querying the database fails, causes the transaction to be rejected.
     */
    private boolean getPinTransactionAuthorization(final PinTransaction transaction) throws SQLException {
        boolean authorized = false;
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCardInfo = databaseConnection.getConnection()
                                                    .prepareStatement(SQLStatements.getPinCard);
        getCardInfo.setLong(1, transaction.getCardNumber());
        ResultSet cardInfo = getCardInfo.executeQuery();
        if (cardInfo.next()) {
            if (transaction.getSourceAccountNumber().equals(cardInfo.getString("account_number"))) {
                final String pinCode = transaction.getPinCode();
                if ((transaction.getTransactionAmount() < CONTACTLESS_TRANSACTION_LIMIT && pinCode == null)
                        || (pinCode.equals(cardInfo.getString("pin_code")))) {
                    if (cardInfo.getDate("expiration_date").after(new Date())) { //check if expiration date is after current date
                        authorized = true;
                    }
                }
            }
        }
        getCardInfo.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return authorized;
    }

    private String getAccountNumberWithCardNumber(final Long cardNumber) throws SQLException, IncorrectPinException {
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
     * if the request was successfull, and sending it off for processing if it was, or sending a rejection to the
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
                        Transaction reply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody),
                                                                    Transaction.class);
                        processTransactionReply(reply, request, callbackBuilder);
                    } else {
                        System.out.printf("%s Transaction request failed, sending rejection.\n", PREFIX);
                        callbackBuilder.build().reject("PIN: Transaction failed.");
                    }
                });
    }

    /**
     * Processes a transaction reply by checking if it was successfull, and the request that was executed matches the
     * request that was sent and sends the matching callback to the request source.
     * @param reply Transaction reply for the transaction request that was made.
     * @param request Transaction request that was sent to the Transaction Dispatch service.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void processTransactionReply(final Transaction reply, final Transaction request,
                                         final CallbackBuilder callbackBuilder) {
        if (reply.isProcessed() && reply.equalsRequest(request)) {
            if (reply.isSuccessful()) {
                System.out.printf("%s Pin transaction was successfull, sending callback.\n", PREFIX);
                callbackBuilder.build().reply(jsonConverter.toJson(reply));
            } else {
                System.out.printf("%s Pin transaction was unsuccessfull, sending rejection.\n", PREFIX);
                callbackBuilder.build().reject("PIN: Pin Transaction was unsuccessfull.");
            }
        } else {
            System.out.printf("%s Pin transaction couldn't be processed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reject("PIN: Pin Transaction couldn't be processed.");
        }
    }

    /**
     * Creates a callbackbuilder so the result of the new pin card request can be sent to the request source and then
     * calls the correct exception handler to execute the request.
     * @param callback Used to send the result of the request to the request source.
     * @param customerId CustomerId of the customer that wants a new pin card.
     * @param accountNumber AccountNumber the pin card should be created for.
     */
    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void addNewPinCard(final Callback<String> callback, final @RequestParam("customerId") String customerId,
                              final @RequestParam("accountNumber") String accountNumber) {
        System.out.printf("%s Received new pin card request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewPinCardExceptions(customerId, accountNumber, callbackBuilder);
    }

    /**
     * Creates a new pin card for the Customer with customerId and accountNumber if the creation succeeds sends the
     * result back to the request source, otherwise rejects the request.
     * @param customerId CustomerId of the customer that requested a new card.
     * @param accountNumber AccountNumber the card should be created for.
     * @param callbackBuilder Used to send the creation result to the request source.
     */
    private void handleNewPinCardExceptions(final String customerId, final String accountNumber,
                                            final CallbackBuilder callbackBuilder) {
        try {
            Long cardNumber = getNextAvailableCardNumber();
            String pinCode = generatePinCode();
            Date expirationDate = generateExpirationDate();
            PinCard pinCard = JSONParser.createJsonPinCard(accountNumber, cardNumber, pinCode,
                                                            Long.parseLong(customerId), expirationDate);
            addPinCardToDatabase(pinCard);
            sendNewPinCardCallback(pinCard, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Something went wrong connecting to the pin database.");
        } catch (NumberFormatException e) {
            callbackBuilder.build().reject("Something went wrong when parsing the customerId in Pin.");
        } catch (NoSuchAlgorithmException e) {
            callbackBuilder.build().reject("Couldn't generate pinCode in PinService.");
        }
    }

    /**
     * Fetches the next available card number by selecting the highest cardNumber from the database and adding 1 to it.
     * @return First available card number that should be used to create a pin card.
     * @throws SQLException Thrown when the datbase cant be reached, will cause a new card request to be rejected.
     */
    private Long getNextAvailableCardNumber() throws SQLException {
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
     * Generates an expiration date for a pin card by adding the valid card duration to the current date.
     * @return Expiration date for a new pin card.
     */
    private Date generateExpirationDate() {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.YEAR, VALID_CARD_DURATION);
        return c.getTime();
    }

    /**
     * Inserts a pin card into the pin database.
     * @param pinCard Pincard to be inserted into the database.
     * @throws SQLException Thrown when the insertion fails, will reject the new pin card request.
     */
    private void addPinCardToDatabase(final PinCard pinCard) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement addPinCard = databaseConnection.getConnection()
                                                         .prepareStatement(SQLStatements.addPinCard);
        addPinCard.setString(1, pinCard.getAccountNumber());
        addPinCard.setLong(2, pinCard.getCustomerId());
        addPinCard.setLong(3, pinCard.getCardNumber());
        addPinCard.setString(4, pinCard.getPinCode());
        addPinCard.setDate(5, new java.sql.Date(pinCard.getExpirationDate().getTime()));
        addPinCard.execute();
        addPinCard.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }


    private void sendNewPinCardCallback(final PinCard pinCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully created pin card, card #%s, accountno. %s  sending callback\n", PREFIX,
                          pinCard.getCardNumber(), pinCard.getAccountNumber());
        callbackBuilder.build().reply(jsonConverter.toJson(pinCard));
    }

    /**
     * Creates a callbackbuilder to send the result of the request to and then calls the exception handler to execute
     * the pin card removal. Sends a callback if the removal is successfull or a rejection if the removal fails.
     * @param callback Used to send the result of the request to the request source.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     */
    @RequestMapping(value = "/card/remove", method = RequestMethod.PUT)
    public void removePinCard(final Callback<String> callback, final @RequestParam("pinCard") String pinCardJson) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleRemovePinCardExceptions(pinCardJson, callbackBuilder);
    }

    /**
     * Tries to create a {@link PinCard} from the Json string and then delete it from the database. Sends a rejection
     * if this fails or a callback with the {@link PinCard} that was removed from the system if it is successfull.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleRemovePinCardExceptions(final String pinCardJson, final CallbackBuilder callbackBuilder) {
        try {
            PinCard pinCard = jsonConverter.fromJson(pinCardJson, PinCard.class);
            deletePinCardFromDatabase(pinCard);
            sendDeletePinCardCallback(pinCard, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Something went wrong connecting to the pin database.");
        } catch (NumberFormatException e) {
            callbackBuilder.build().reject("Something went wrong when parsing the customerId in Pin.");
        }
    }

    /**
     * Deletes a pincard from the pin database.
     * @param pinCard Pin card that should be deleted from the database.
     * @throws SQLException Thrown when the sql query fails, will cause the removal request to be rejected.
     * @throws NumberFormatException Cause when a parameter is incorrectly specified, will cause the removal request
     * to be rejected.
     */
    private void deletePinCardFromDatabase(final PinCard pinCard) throws SQLException, NumberFormatException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement removePinCard = databaseConnection.getConnection()
                                                            .prepareStatement(SQLStatements.removePinCard);
        removePinCard.setString(1, pinCard.getAccountNumber());
        removePinCard.setLong(2, pinCard.getCustomerId());
        removePinCard.setLong(3, pinCard.getCardNumber());
        removePinCard.setString(4, pinCard.getPinCode());
        removePinCard.execute();
    }

    private void sendDeletePinCardCallback(final PinCard pinCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Pin card #%s successfully deleted from the system, sending callback.\n", PREFIX,
                          pinCard.getCardNumber());
        callbackBuilder.build().reply(jsonConverter.toJson(pinCard));
    }
}
