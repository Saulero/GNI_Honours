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

import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;

import static database.SQLStatements.*;
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
    /** Used to set how long a pin card is valid. */
    private static final int VALID_CARD_DURATION = 5;
    /** Used to check if a transaction without a pincode is authorized. */
    private static final int CONTACTLESS_TRANSACTION_LIMIT = 25;
    /** Used to check if accountNumber are of the correct length. */
    private static int accountNumberLength = 18;
    /** Account number where fees are transferred to. */
    private static final String GNI_ACCOUNT = "NL52GNIB3676451168";
    /** Credit card fee. */
    private static final double MONTHLY_CREDIT_CARD_FEE = 5.00;
    /** Credit card limit. */
    private static final double CREDIT_CARD_LIMIT = 1000;

    /**
     * Constructor.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    PinService(final int servicePort, final String serviceHost,
                      final int sysInfoPort, final String sysInfoHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
        this.databaseConnectionPool = new ConnectionPool();
        this.jsonConverter = new Gson();
        sendServiceInformation(servicePort, serviceHost);
    }

    /**
     * Method that sends the service information of this service to the SystemInformationService.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     */
    private void sendServiceInformation(final int servicePort, final String serviceHost) {
        ServiceInformation serviceInfo = new ServiceInformation(servicePort, serviceHost, ServiceType.PIN_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", jsonConverter.toJson(serviceInfo), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        System.err.println("Problem with connection to the SystemInformationService.");
                        System.err.println("Shutting down the Pin service.");
                        System.exit(1);
                    }
                });
    }

    /**
     * Method that initializes all connections to other services once it knows their addresses.
     * @param callback Callback to the source of the request.
     * @param systemInfo Json string containing all System Information.
     */
    @RequestMapping(value = "/start", method = RequestMethod.PUT)
    public void startService(final Callback<String> callback, @RequestParam("sysInfo") final String systemInfo) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(systemInfo), MessageWrapper.class);

        SystemInformation sysInfo = (SystemInformation) messageWrapper.getData();
        ServiceInformation transactionIn = sysInfo.getTransactionReceiveServiceInformation();
        ServiceInformation transactionOut = sysInfo.getTransactionDispatchServiceInformation();

        this.transactionReceiveClient = httpClientBuilder().setHost(transactionIn.getServiceHost())
                .setPort(transactionIn.getServicePort()).buildAndStart();
        this.transactionDispatchClient = httpClientBuilder().setHost(transactionOut.getServiceHost())
                .setPort(transactionOut.getServicePort()).buildAndStart();

        System.out.printf("%s Initialization of Pin service connections complete.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
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
        System.out.printf("%s Received new Pin transaction from a customer.\n", PREFIX);
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
            } else if (request.isCreditCardTransaction()) {
                CreditCard creditCard = getCreditCardData(request.getCardNumber());
                getCreditCardTransactionAuthorization(request, creditCard, callbackBuilder);
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
        } catch (CardDeactivatedException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 421, e.getMessage(),
                            "The pin card used has been permanently deactivated.")));
        }
    }


    private CreditCard getCreditCardData(final Long creditCardNumber) throws SQLException, IncorrectInputException {
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement getCreditCardInfo = connection.getConnection()
                                                        .prepareStatement(SQLStatements.getCreditCardInfo);
        getCreditCardInfo.setLong(1, creditCardNumber);
        ResultSet cardInfo = getCreditCardInfo.executeQuery();
        CreditCard creditCard = new CreditCard();
        if (cardInfo.next()) {
            creditCard.setCreditCardNumber(creditCardNumber);
            creditCard.setAccountNumber(cardInfo.getString("account_number"));
            creditCard.setPinCode(cardInfo.getString("pin_code"));
            creditCard.setLimit(cardInfo.getDouble("credit_limit"));
            creditCard.setBalance(cardInfo.getDouble("balance"));
            creditCard.setFee(cardInfo.getDouble("card_fee"));
            creditCard.setActivationDate(cardInfo.getDate("active_from").toLocalDate());
            creditCard.setActive(cardInfo.getBoolean("active"));
            creditCard.setIncorrect_attempts(cardInfo.getLong("incorrect_attempts"));
        } else {
            throw new IncorrectInputException("There does not exist a credit card with this card number.");
        }
        getCreditCardInfo.close();
        databaseConnectionPool.returnConnection(connection);
        return creditCard;
    }

    /**
     * Creates a new {@link Transaction} object from a {@link PinTransaction} object.
     * @param pinTransaction ATM withdrawal/deposit to convert to a {@link Transaction}.
     * @param cardAccountNumber AccountNumber of the card used in the transaction.
     * @return Transaction object representing the ATM withdrawal/deposit that is to be executed.
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
                    cardInfo.getDate("expiration_date").toLocalDate(),
                    cardInfo.getBoolean("active"));
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
            incrementIncorrectAttempts(pinTransaction.getCardNumber(), false);
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
                        if (pinCard.isActive()) {
                            LocalDate systemDate = (LocalDate) messageWrapper.getData();
                            if (pinCard.getExpirationDate().isAfter(systemDate)) {
                                try {
                                    if (pinCard.getPinCode().equals(pinTransaction.getPinCode())) {
                                        try {
                                            // reset the count
                                            unblockCard(pinCard.getCardNumber(), pinCard.getAccountNumber(), false);
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
                                        incrementIncorrectAttempts(pinTransaction.getCardNumber(), false);
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
                            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                    .createMessageWrapper(true, 421,
                                            "The card used is not active.",
                                            "The pin card used has been permanently deactivated.")));
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
     * @param isCreditCard boolean indicating if the increment statement is for a credit card or a pin card.
     * @throws SQLException Thrown when there is an error connecting to the database.
     */
    private void incrementIncorrectAttempts(final Long cardNumber, boolean isCreditCard) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement incrementStatement;
        if (isCreditCard) {
            incrementStatement = databaseConnection.getConnection()
                    .prepareStatement(SQLStatements.incrementIncorrectCreditcardAttempts);
        } else {
            incrementStatement = databaseConnection.getConnection()
                    .prepareStatement(SQLStatements.incrementIncorrectPincardAttempts);
        }

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
     * @throws IncorrectPinException Thrown when the pin code used in the transaction is incorrect.
     * @throws CardExpiredException Thrown when the card used in the transaction is expired.
     * @throws IncorrectInputException Thrown when a field is incorrectly specified.
     * @throws CardDeactivatedException Thrown when the card has previously been permanently deactivated.
     */
    void getPinTransactionAuthorization(final PinTransaction pinTransaction,
                                        final CallbackBuilder callbackBuilder) throws SQLException,
                                        CardExpiredException, IncorrectInputException, IncorrectPinException,
                                        CardBlockedException, CardDeactivatedException {
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
                    cardInfo.getDate("expiration_date").toLocalDate(),
                    cardInfo.getBoolean("active"));
            if (pinTransaction.getSourceAccountNumber().equals(accountNumberLinkedToCard)) {
                if (incorrectAttempts < 3) {
                    if (pinCodeUsed == null) {
                        if (pinTransaction.getTransactionAmount() < CONTACTLESS_TRANSACTION_LIMIT) {
                            checkPinValidity(pinCard, pinTransaction, false, callbackBuilder);
                        } else {
                            getCardInfo.close();
                            databaseConnectionPool.returnConnection(databaseConnection);
                            throw new IncorrectPinException(
                                    "The transfer amount is too high, please enter pin code.");
                        }
                    } else if (pinCodeUsed.equals(pinCard.getPinCode())) {
                        checkPinValidity(pinCard, pinTransaction, false, callbackBuilder);
                    } else {
                        getCardInfo.close();
                        databaseConnectionPool.returnConnection(databaseConnection);
                        incrementIncorrectAttempts(pinCard.getCardNumber(), false);
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
                incrementIncorrectAttempts(pinTransaction.getCardNumber(), false);
                throw new IncorrectPinException("Pin card does not belong to accountNumber used in the transaction.");
            }
        } else {
            getCardInfo.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            incrementIncorrectAttempts(pinTransaction.getCardNumber(), false);
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

    private void getCreditCardTransactionAuthorization(final PinTransaction pinTransaction, final CreditCard creditCard,
                                                       final CallbackBuilder callbackBuilder)
            throws SQLException, IncorrectPinException, CardExpiredException, CardBlockedException {
        systemInformationClient.getAsync("/services/systemInfo/date",
                (httpStatusCode, contentType, body) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser
                        .removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    LocalDate systemDate = (LocalDate) messageWrapper.getData();
                    String sourceAccountNumber = pinTransaction.getSourceAccountNumber();
                    sourceAccountNumber = sourceAccountNumber.substring(0, sourceAccountNumber.length() - 1); // remove C from accountNumber
                    if (systemDate.isBefore(creditCard.getActivationDate())
                            || !creditCard.isActive()) {
                        System.out.printf("%s Card is inactive, sending callback.\n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                .createMessageWrapper(true, 421,
                                        "The card used is not active.",
                                        "The credit card used is not active yet or expired.")));
                    } else if (creditCard.getIncorrect_attempts() > 2) {
                        System.out.printf("%s Card is blocked sending callback./n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                .createMessageWrapper(true, 419, "The card used is currently blocked.",
                                        "The pin card used does not have the authorization to perform this request.")));
                    } else if (!creditCard.getPinCode().equals(pinTransaction.getPinCode())) {
                        System.out.printf("%s Pincode incorrect, sending callback.\n", PREFIX);
                        try {
                            incrementIncorrectAttempts(creditCard.getCreditCardNumber(), true);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                .createMessageWrapper(true, 421,
                                        "The pin code used is incorrect.",
                                        "An invalid PINcard, -code or -combination was used.")));
                    } else if (!creditCard.getAccountNumber().equals(sourceAccountNumber)) {
                        System.out.printf("%s Creditcard does not belong to that accountnumber, sending callback.\n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                .createMessageWrapper(true, 419,
                                        "Pin card does not belong to accountNumber used in the transaction.",
                                        "The pin card used does not have the authorization to perform this request.")));
                    } else if (creditCard.getBalance() < pinTransaction.getTransactionAmount()) {
                        System.out.printf("%s Credit card does not have enough balance to process transaction.\n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                .createMessageWrapper(true, 418,
                                        "There are not enough funds on the credit card to make the transaction.",
                                        "The balance on the credit card used is not high enough.")));
                    } else {
                        creditCard.processTransaction(pinTransaction);
                        updateCreditCardBalanceInDb(creditCard);
                        addCreditCardTransactionToDb(pinTransaction, creditCard.getBalance(), systemDate);
                        if (pinTransaction.getDestinationAccountNumber().contains("GNI")) {
                            Transaction transactionToProcess = new Transaction();
                            transactionToProcess.setTransactionAmount(pinTransaction.getTransactionAmount());
                            transactionToProcess.setSourceAccountNumber(creditCard.getAccountNumber());
                            transactionToProcess.setDestinationAccountNumber(pinTransaction.getDestinationAccountNumber());
                            transactionToProcess.setDestinationAccountHolderName(pinTransaction.getDestinationAccountHolderName());
                            transactionToProcess.setDescription("Credit card transaction.");
                            doTransactionReceiveRequest(transactionToProcess, callbackBuilder);
                        } else {
                            sendCreditCardTransactionCallback(callbackBuilder);
                        }
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

    private void updateCreditCardBalanceInDb(final CreditCard creditCard) {
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement updateBalanceStatement = connection.getConnection()
                    .prepareStatement(SQLStatements.updateCreditCardBalance);
            updateBalanceStatement.setDouble(1, creditCard.getBalance());
            updateBalanceStatement.setLong(2, creditCard.getCreditCardNumber());
            updateBalanceStatement.execute();
            updateBalanceStatement.close();
            databaseConnectionPool.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addCreditCardTransactionToDb(final PinTransaction pinTransaction,
                                              final Double newBalance,
                                              final LocalDate currentDate) {
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement addTransactionStatement = connection.getConnection()
                    .prepareStatement(SQLStatements.addCreditCardTransaction);
            addTransactionStatement.setLong(1, getCreditCardTransactionId());
            addTransactionStatement.setDate(2, java.sql.Date.valueOf(currentDate));
            addTransactionStatement.setLong(3, pinTransaction.getCardNumber());
            addTransactionStatement.setString(4, pinTransaction.getDestinationAccountNumber());
            addTransactionStatement.setDouble(5, pinTransaction.getTransactionAmount());
            addTransactionStatement.setDouble(6, newBalance);
            addTransactionStatement.execute();
            addTransactionStatement.close();
            databaseConnectionPool.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Long getCreditCardTransactionId() {
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement highestIdStatement = connection.getConnection()
                    .prepareStatement(SQLStatements.getHighestCreditCardTransactionId);
            ResultSet idResult = highestIdStatement.executeQuery();
            Long id;
            if (idResult.next()) {
                id = idResult.getLong(1) + 1;
            } else {
                id = 1L;
            }
            highestIdStatement.close();
            databaseConnectionPool.returnConnection(connection);
            return id;
        } catch (SQLException e) {
            e.printStackTrace();
            return 1L;
        }
    }

    private void doTransactionReceiveRequest(final Transaction transaction,
                                             final CallbackBuilder callbackBuilder) {
        transactionReceiveClient.putFormAsyncWith1Param("/services/transactionReceive/transaction",
                "request", jsonConverter.toJson(transaction),
                (statusCode, httpContentType, replyBody) -> {
                    processTransactionReceiveReply(statusCode, replyBody, callbackBuilder);
                });
    }

    private void sendCreditCardTransactionCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Credit card transaction was successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply")));
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
        transactionDispatchClient.putFormAsyncWith3Params("/services/transactionDispatch/transaction",
                "request", jsonConverter.toJson(request), "customerId", customerId,
                "override", false,
        (code, contentType, replyBody) -> {
            processTransactionDispatchReply(code, replyBody, callbackBuilder);
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
            processTransactionReceiveReply(code, body, callbackBuilder);
        }));
    }

    /**
     * Processes a transaction reply by checking if it was successful, and the request that was executed matches the
     * request that was sent and sends the matching callback to the request source.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void processTransactionReceiveReply(final int statusCode,
                                                final String replyBody,
                                                final CallbackBuilder callbackBuilder) {
        if (statusCode == HTTP_OK) {
            MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody),
                    MessageWrapper.class);
            if (!messageWrapper.isError()) {
                Transaction reply = (Transaction) messageWrapper.getData();
                if (reply.isSuccessful()) {
                    System.out.printf("%s Pin transaction was successful, sending callback.\n", PREFIX);
                    callbackBuilder.build().reply(replyBody);
                } else {
                    System.out.printf("%s Pin transaction was unsuccessful, sending rejection.\n", PREFIX);
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                            "Unknown error occurred.")));
                }
            } else {
                callbackBuilder.build().reply(replyBody);
            }
        } else {
            System.out.printf("%s Transaction request failed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                    500, "An unknown error occurred.",
                    "There was a problem with one of the HTTP requests")));
        }
    }

    private void processTransactionDispatchReply(final int statusCode, final String replyBody,
                                                 final CallbackBuilder callbackBuilder) {
        if (statusCode == HTTP_OK) {
            MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody),
                    MessageWrapper.class);
            if (!messageWrapper.isError()) {
                Transaction reply = (Transaction) messageWrapper.getData();
                if (reply.isSuccessful()) {
                    doTransactionReceiveRequest(reply, callbackBuilder);
                } else {
                    System.out.printf("%s Pin transaction was unsuccessful, sending rejection.\n", PREFIX);
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                            "Unknown error occurred.")));
                }
            } else {
                callbackBuilder.build().reply(replyBody);
            }
        } else {
            System.out.printf("%s Transaction request failed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                    500, "An unknown error occurred.",
                    "There was a problem with one of the HTTP requests")));
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
        generateExpirationDate(requesterId, ownerId, accountNumber, null, callbackBuilder);
    }

    /**
     * Generates an expiration date for a pin card by adding the valid card duration to the current date.
     * @param requesterId CustomerId of the user that sent the request.
     * @param ownerId CustomerId of the customer that will own the new card.
     * @param accountNumber AccountNumber the card should be created for.
     * @param newPinCode the preset pinCode, in case of a replacement.
     * @param callbackBuilder Used to send the creation result to the request source.
     */
    void generateExpirationDate(final String requesterId, final String ownerId, final String accountNumber,
                                final String newPinCode, final CallbackBuilder callbackBuilder) {
        systemInformationClient.getAsync("/services/systemInfo/date", (httpStatusCode, contentType, body) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                        MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    LocalDate systemDate = (LocalDate) messageWrapper.getData();
                    LocalDate expirationDate = systemDate.plusYears(VALID_CARD_DURATION);
                    handleNewPinCardExceptions(expirationDate, requesterId,
                            ownerId, accountNumber, newPinCode, callbackBuilder);
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
     * @param newPinCode the preset pinCode, in case of a replacement.
     * @param callbackBuilder Used to send the creation result to the request source.
     */
    private void handleNewPinCardExceptions(final LocalDate expirationDate, final String requesterId,
                                            final String ownerId, final String accountNumber, final String newPinCode,
                                            final CallbackBuilder callbackBuilder) {
        try {
            Long cardNumber = getNextAvailableCardNumber();
            String pinCode = newPinCode;
            if (pinCode == null) {
                pinCode = generatePinCode();
            }
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
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to pin database.")));
        } catch (NumberFormatException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.",
                    "Something went wrong when parsing the customerId in Pin.")));
        } catch (NoSuchAlgorithmException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Unknown error occurred.")));
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
     * @param pinCard PinCard to be inserted into the database.
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
        addPinCard.setBoolean(7, true);
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
    public void ProcessUnblockCardRequest(final Callback<String> callback,
                                          final @RequestParam("pinCard") String pinCardJson) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleCardUnblockExceptions(pinCardJson, callbackBuilder);
    }

    /**
     * Tries to create a {@link PinCard} from the Json string and then unblock it. Sends a rejection
     * if this fails or a callback with the {@link PinCard} that was unblocked if it is successful.
     * @param pinCardJson Json String representing a {@link PinCard} that should be unblocked.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleCardUnblockExceptions(final String pinCardJson, final CallbackBuilder callbackBuilder) {
        try {
            PinCard pinCard = jsonConverter.fromJson(pinCardJson, PinCard.class);
            Long cardnumber = pinCard.getCardNumber();
            if (cardnumber > 5248860000000000L && cardnumber < 5248870000000000L) {
                unblockCard(pinCard.getCardNumber(), pinCard.getAccountNumber(), true);
            } else {
                unblockCard(pinCard.getCardNumber(), pinCard.getAccountNumber(), false);
            }
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
     * @param cardNumber cardNumber of the Card that should be unblocked.
     * @param accountNumber accountNumber linked to the card that should be unblocked.
     * @throws SQLException Thrown when the sql query fails, will cause the unblock request to be rejected.
     * @throws NumberFormatException Cause when a parameter is incorrectly specified, will cause the unblock request
     * to be rejected.
     * @throws IncorrectInputException Thrown when any of the provided information appears to be incorrect.
     * @throws NoEffectException Thrown when the pin card wasn't blocked in the first place.
     */
    void unblockCard(final Long cardNumber, final String accountNumber, final boolean isCreditCard) throws SQLException,
            IncorrectInputException, NoEffectException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement getCard;
        if (isCreditCard) {
            getCard = con.getConnection().prepareStatement(SQLStatements.getCreditCardInfo);
        } else {
            getCard = con.getConnection().prepareStatement(SQLStatements.getPinCard);
        }
        getCard.setLong(1, cardNumber);
        ResultSet cardInfo = getCard.executeQuery();
        if (cardInfo.next()) {
            String accountNumberInDb = cardInfo.getString("account_number");
            Long attempts = cardInfo.getLong("incorrect_attempts");
            cardInfo.close();

            if (!accountNumber.equals(accountNumberInDb)) {
                throw new IncorrectInputException(
                        "The provided account number does not match the account number in the system.");
            } else {
                // reset the count
                if (isCreditCard) {
                    getCard = con.getConnection().prepareCall(SQLStatements.unblockCreditCard);
                } else {
                    getCard = con.getConnection().prepareStatement(SQLStatements.unblockPinCard);
                }
                getCard.setLong(1, cardNumber);
                getCard.executeUpdate();

                if (attempts < 3) {
                    throw new NoEffectException("The card was not blocked in the first place, "
                            + "but the attempts count has been reset none the less");
                }
            }
        } else {
            getCard.close();
            throw new IncorrectInputException("The provided pin card does not appear to exist.");
        }

        getCard.close();
        con.close();
        databaseConnectionPool.returnConnection(con);
    }

    /**
     * Sends the correct callback back to the source.
     * @param pinCard The unlocked pinCard.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void sendPinCardUnblockCallback(final PinCard pinCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Card #%s successfully unblocked, sending callback.\n",
                PREFIX, pinCard.getCardNumber());
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", pinCard)));
    }

    /**
     * Creates a callbackBuilder to send the result of the request to and then calls the exception handler to execute
     * the pin card replacement.
     * @param callback Used to send the result of the request to the request source.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     */
    @RequestMapping(value = "/invalidateCard", method = RequestMethod.PUT)
    public void invalidateCard(final Callback<String> callback, final @RequestParam("pinCard") String pinCardJson,
                               final @RequestParam("newPin") boolean newPin) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleRemovePinCardExceptions(pinCardJson, newPin, callbackBuilder);
    }

    /**
     * Tries to create a {@link PinCard} from the Json string and then delete it from the database. Sends a rejection
     * if this fails or a callback with the {@link PinCard} that was removed from the system if it is successful.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleRemovePinCardExceptions(final String pinCardJson, final boolean newPin,
                                               final CallbackBuilder callbackBuilder) {
        try {
            PinCard pinCard = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(pinCardJson), PinCard.class);
            Long cardNumber = pinCard.getCardNumber();
            if (cardNumber > 5248860000000000L && cardNumber < 5248870000000000L) {
                CreditCard cardToRemove = new CreditCard();
                cardToRemove.setCreditCardNumber(cardNumber);
                deactivateCreditCard(cardToRemove);
                withdrawPaymentForNewCard(pinCard, true, newPin, callbackBuilder);
            } else {
                deactivatePinCard(pinCard);
                // TODO WHAT IF NOT ENOUGH BALANCE? - SHOULD IT WITHDRAW ANYWAY(?)
                // get payment for new card
                withdrawPaymentForNewCard(pinCard, false, newPin, callbackBuilder);
            }
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the Pin database.")));
        } catch (NumberFormatException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.",
                    "Something went wrong when parsing the customerId in Pin.")));
        } catch (InvalidParameterException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 418, e.getMessage())));
        }
    }

    /**
     * Gets the pin code from a card number.
     * @param pinCard Pin card that should be queried from the database.
     * @throws SQLException Thrown when the sql query fails.
     */
    private String getPinCodeFromCard(final PinCard pinCard) throws SQLException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection()
                .prepareStatement(SQLStatements.getPinCard);
        ps.setLong(1, pinCard.getCardNumber());
        ResultSet rs = ps.executeQuery();

        String res = null;
        if (rs.next()) {
            res = rs.getString("pin_code");
        }

        con.close();
        databaseConnectionPool.returnConnection(con);
        return res;
    }

    /**
     * Deletes a pinCard from the pin database.
     * @param pinCard Pin card that should be deleted from the database.
     * @throws SQLException Thrown when the sql query fails, will cause the removal request to be rejected.
     * @throws NumberFormatException Cause when a parameter is incorrectly specified, will cause the removal request
     * to be rejected.
     * @throws InvalidParameterException Thrown when there was no card found to deactivate.
     */
    private void deactivatePinCard(final PinCard pinCard)
            throws SQLException, NumberFormatException, InvalidParameterException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement removePinCard = databaseConnection.getConnection()
                .prepareStatement(SQLStatements.deactivatePinCard);
        removePinCard.setString(1, pinCard.getAccountNumber());
        removePinCard.setLong(2, pinCard.getCustomerId());
        removePinCard.setLong(3, pinCard.getCardNumber());
        int success = removePinCard.executeUpdate();
        databaseConnection.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        if (success == 0) {
            throw new InvalidParameterException("Card not found.");
        }
    }

    /**
     * Withdraws 7.50 from a customers account as payment for a replacement PIN Card.
     * @param pinCard A {@link PinCard} with the account of the customer.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void withdrawPaymentForNewCard(final PinCard pinCard, final boolean isCreditCard, final boolean newPin,
                                           final CallbackBuilder callbackBuilder) {
        Transaction request = JSONParser.createJsonTransaction(-1,
                pinCard.getAccountNumber(), GNI_ACCOUNT, "GNI Bank",
                "Fees for replacement of old PIN Card #" + pinCard.getCardNumber(),
                7.50, false, false);
        transactionDispatchClient.putFormAsyncWith3Params("/services/transactionDispatch/transaction",
                "request", jsonConverter.toJson(request), "customerId", pinCard.getCustomerId(),
                "override", true,
                (code, contentType, replyBody) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyBody), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            try {
                                if (isCreditCard) {
                                    String pinCode;
                                    if (newPin) {
                                        pinCode = generatePinCode();
                                    } else {
                                        pinCode = getCreditCardData(pinCard.getCardNumber()).getPinCode();
                                    }
                                    getCurrentDateForCreditCard(pinCard.getAccountNumber(), pinCode, callbackBuilder);
                                } else {
                                    // create new card & send callback
                                    String id = "" + pinCard.getCustomerId();
                                    String pinCode;
                                    if (!newPin) {
                                        pinCode = getPinCodeFromCard(pinCard);
                                    } else {
                                        pinCode = generatePinCode();
                                    }
                                    generateExpirationDate(id, id, pinCard.getAccountNumber(), pinCode, callbackBuilder);
                                }
                            } catch (SQLException | NoSuchAlgorithmException e) {
                                e.printStackTrace();
                                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                                        .createMessageWrapper(true, 500,
                                                "An unknown error occurred.",
                                                "There was a problem with one of the HTTP requests")));
                            } catch (IncorrectInputException e) {
                                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                        true, 418, e.getMessage())));
                            }
                        } else {
                            callbackBuilder.build().reply(replyBody);
                        }
                    } else {
                        System.out.printf("%s Transaction request failed, sending rejection.\n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    @RequestMapping(value = "/creditCard", method = RequestMethod.PUT)
    public void processNewCreditCard(final Callback<String> callback,
                                     @RequestParam("accountNumber") final String accountNumber) {
        System.out.printf("%s Received new credit card request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        getCurrentDateForCreditCard(accountNumber, null, callbackBuilder);
    }

    private void getCurrentDateForCreditCard(final String accountNumber, final String pinCode,
                                             final CallbackBuilder callbackBuilder) {
        try {
            findActiveCreditCard(getCreditCardsFromAccountNr(accountNumber));
            // If IncorrectInputException is not thrown this means the account already has an active creditCard.
            System.out.printf("%s Account already has an active creditCard, rejecting request.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "The accountNumber already has a creditCard",
                    "Cannot create new creditCard because there is already a card linked to this accountNumber..")));
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser
                    .createMessageWrapper(true, 500,
                            "An unknown error occurred.",
                            "There was a problem with one of the HTTP requests")));
        } catch (IncorrectInputException e) {
            systemInformationClient.getAsync("/services/systemInfo/date", (code, contentType, body) -> {
                if (code == HTTP_OK) {
                    MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                                                                        MessageWrapper.class);
                    if (!messageWrapper.isError()) {
                        LocalDate currentDate = (LocalDate) messageWrapper.getData();
                        handleNewCreditCardExceptions(accountNumber, pinCode, currentDate, callbackBuilder);
                    } else {
                        callbackBuilder.build().reply(body);
                    }
                } else {
                    System.out.printf("%s Processing new credit card failed, body: %s\n\n\n\n", PREFIX, body);
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                            500, "An unknown error occurred.",
                            "There was a problem with one of the HTTP requests")));
                }
            });
        }
    }

    private void handleNewCreditCardExceptions(final String accountNumber, final String pinCode,
                                               final LocalDate currentDate,
                                               final CallbackBuilder callbackBuilder) {
        try {
            CreditCard creditCard = new CreditCard();
            creditCard.setAccountNumber(accountNumber);
            creditCard.setLimit(CREDIT_CARD_LIMIT);
            creditCard.setBalance(CREDIT_CARD_LIMIT);
            creditCard.setIncorrect_attempts(0L);
            creditCard.setFee(MONTHLY_CREDIT_CARD_FEE);
            creditCard.setPinCode(pinCode);
            creditCard = addNewCreditCardToDb(creditCard, currentDate);
            sendNewCreditCardCallback(creditCard, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the Credit card database.")));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Internal error occurred.")));
        }
    }

    private CreditCard addNewCreditCardToDb(final CreditCard creditCard, final LocalDate currentDate)
            throws SQLException, NoSuchAlgorithmException {
        SQLConnection connection = databaseConnectionPool.getConnection();
        LocalDate activationDate = currentDate.plusDays(1L);
        creditCard.setCreditCardNumber(generateCreditCardNumber());
        if (creditCard.getPinCode() == null) {
            creditCard.setPinCode(generatePinCode());
        }
        creditCard.setActivationDate(activationDate);
        creditCard.setActive(true);

        PreparedStatement ps = connection.getConnection().prepareStatement(addCreditCard);
        ps.setLong(1, creditCard.getCreditCardNumber());
        ps.setString(2, creditCard.getAccountNumber());
        ps.setString(3, creditCard.getPinCode());
        ps.setLong(4, creditCard.getIncorrect_attempts());
        ps.setDouble(5, creditCard.getLimit());
        ps.setDouble(6, creditCard.getBalance());
        ps.setDouble(7, creditCard.getFee());
        ps.setDate(8, java.sql.Date.valueOf(creditCard.getActivationDate()));
        ps.setBoolean(9, creditCard.isActive());
        ps.executeUpdate();
        ps.close();
        databaseConnectionPool.returnConnection(connection);
        return creditCard;
    }

    private Long generateCreditCardNumber() throws SQLException {
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement getHighestId = connection.getConnection().prepareStatement(getHighestCreditCardID);
        ResultSet highestIdResult = getHighestId.executeQuery();
        Long newCreditCardNumber;
        if (highestIdResult.next()) {
            if (highestIdResult.getLong(1) == 0L) {
                newCreditCardNumber = 5248860000000001L;
            } else {
                newCreditCardNumber = highestIdResult.getLong(1) + 1;
            }
        } else {
            newCreditCardNumber = 5248860000000001L;
        }
        return newCreditCardNumber;
    }

    private void sendNewCreditCardCallback(final CreditCard creditCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New credit card request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", creditCard)));
    }

    @RequestMapping(value = "/creditCard/remove", method = RequestMethod.PUT)
    public void processCreditCardRemoval(final Callback<String> callback,
                                        @RequestParam("accountNumber") final String accountNumber,
                                        @RequestParam("customerId") final Long customerId) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleCreditCardRemovalExceptions(accountNumber, customerId, callbackBuilder);
    }

    private void handleCreditCardRemovalExceptions(final String accountNumber, final Long customerId,
                                                   final CallbackBuilder callbackBuilder) {
        try {
            CreditCard creditCard = findActiveCreditCard(getCreditCardsFromAccountNr(accountNumber));
            handleCreditCardRemovalBalance(creditCard, customerId, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Unknown error occurred.")));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    e.getMessage(), "The accountNumber used does not have a credit card.")));
        }
    }

    private List<CreditCard> getCreditCardsFromAccountNr(final String accountNumber) throws SQLException,
            IncorrectInputException {
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement getCards = connection.getConnection().prepareStatement(getCreditCardsFromAccountNumber);
        getCards.setString(1, accountNumber);
        ResultSet cardResult = getCards.executeQuery();
        List<CreditCard> cardList = new LinkedList<>();
        while (cardResult.next()) {
            CreditCard card = new CreditCard();
            card.setCreditCardNumber(cardResult.getLong("card_number"));
            card.setAccountNumber(accountNumber);
            card.setPinCode(cardResult.getString("pin_code"));
            card.setIncorrect_attempts(cardResult.getLong("incorrect_attempts"));
            card.setLimit(cardResult.getDouble("credit_limit"));
            card.setBalance(cardResult.getDouble("balance"));
            card.setFee(cardResult.getDouble("card_fee"));
            card.setActivationDate(cardResult.getDate("active_from").toLocalDate());
            card.setActive(cardResult.getBoolean("active"));
            cardList.add(card);
        } if (cardList.size() > 0) {
            return cardList;
        } else {
            getCards.close();
            databaseConnectionPool.returnConnection(connection);
            throw new IncorrectInputException("Account does not have a credit card.");
        }
    }

    private CreditCard findActiveCreditCard(final List<CreditCard> cardList) throws IncorrectInputException {
        for (CreditCard card : cardList) {
            if (card.isActive()) {
                return card;
            }
        }
        throw new IncorrectInputException("There is no active credit card for this account.");
    }

    private void handleCreditCardRemovalBalance(final CreditCard creditCard, final Long customerId,
                                                final CallbackBuilder callbackBuilder) throws SQLException {
        if (!creditCard.getBalance().equals(creditCard.getLimit())) {
            // try to withdraw funds from the account linked to the CC
            LinkedList<CreditCard> cardList = new LinkedList<>();
            cardList.add(creditCard);
            refillCreditCards(cardList, customerId, true, callbackBuilder);
        } else {
            deactivateCreditCard(creditCard);
            sendRemoveCreditCardCallback(callbackBuilder);
        }
    }

    private void deactivateCreditCard(final CreditCard creditCard) throws SQLException {
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement removeCard = connection.getConnection().prepareStatement(deactivateCreditCard);
        removeCard.setLong(1, creditCard.getCreditCardNumber());
        removeCard.execute();
        removeCard.close();
        databaseConnectionPool.returnConnection(connection);
    }

    private void refillCreditCards(final List<CreditCard> creditCards, final Long customerId, final boolean closeCard,
                                  final CallbackBuilder callbackBuilder) {
        if (creditCards.size() < 1) {
            sendRemoveCreditCardCallback(callbackBuilder);
        } else {
            CreditCard creditCard = creditCards.get(0);
            Transaction transaction = new Transaction();
            transaction.setSourceAccountNumber(creditCard.getAccountNumber());
            transaction.setDestinationAccountNumber(GNI_ACCOUNT);
            transaction.setDestinationAccountHolderName("GNI BANK");
            transaction.setTransactionAmount(creditCard.getLimit() - creditCard.getBalance());
            transaction.setDescription("Refill of credit card #" + creditCard.getCreditCardNumber());
            transactionDispatchClient.putFormAsyncWith3Params("/services/transactionDispatch/transaction",
                    "request", jsonConverter.toJson(transaction), "customerId", customerId,
                    "override", !closeCard, //if the card should not be closed this method is being called by an admin.
                    (code, contentType, replyBody) -> handleDispatchRefillResponse(code, replyBody, transaction,
                            creditCards, customerId, closeCard, callbackBuilder));
        }
    }

    private void handleDispatchRefillResponse(final int code, final String replyBody, final Transaction transaction,
                                              final List<CreditCard> creditCards, final Long customerId,
                                              final boolean closeCard,
                                              final CallbackBuilder callbackBuilder) {
        if (code == HTTP_OK) {
            MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody),
                    MessageWrapper.class);
            if (!messageWrapper.isError()) {
                Transaction reply = (Transaction) messageWrapper.getData();
                if (reply.isSuccessful()) {
                    if (closeCard) {
                        transactionReceiveClient.putFormAsyncWith3Params("/services/transactionReceive/transaction",
                                "request", jsonConverter.toJson(transaction), "customerId", customerId,
                                "override", false, (receiveStatusCode, httpContentType, replyJson)
                                        -> handleReceiveRefillResponse(receiveStatusCode, replyJson, creditCards,
                                        callbackBuilder));
                    } else {
                        CreditCard processedCard = creditCards.remove(0); // remove card that has been processed
                        processedCard.setBalance(processedCard.getLimit());
                        updateCreditCardBalanceInDb(processedCard);
                        if (creditCards.size() > 0) {
                            refillCreditCards(creditCards, 0L, false, callbackBuilder);
                        } else {
                            System.out.printf("%s Credit cards successfully refilled, sending callback.\n", PREFIX);
                            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                    false, 200, "Normal Reply")));
                        }
                    }
                } else {
                    System.out.printf("%s Credit card refill unsuccessfull, sending rejection.\n", PREFIX);
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                            "Unknown error occurred, possibly not enough funds to refill the credit card.")));
                }
            } else {
                callbackBuilder.build().reply(replyBody);
            }
        } else {
            System.out.printf("%s Credit card refill request failed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                    500, "An unknown error occurred.",
                    "There was a problem with one of the HTTP requests")));
        }
    }

    private void handleReceiveRefillResponse(final int code, final String replyBody, final List<CreditCard> creditCards,
                                             final CallbackBuilder callbackBuilder) {
        if (code == HTTP_OK) {
            MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody),
                    MessageWrapper.class);
            if (!messageWrapper.isError()) {
                Transaction reply = (Transaction) messageWrapper.getData();
                if (reply.isSuccessful()) {
                    try {
                        deactivateCreditCard(creditCards.get(0));
                        sendRemoveCreditCardCallback(callbackBuilder);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "Unknown error occurred.")));
                    }
                } else {
                    System.out.printf("%s Credit card refill unsuccessfull, sending rejection.\n", PREFIX);
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                            "Unknown error occurred, possibly not enough funds to refill the credit card.")));
                }
            } else {
                callbackBuilder.build().reply(replyBody);
            }
        } else {
            System.out.printf("%s Credit card refill request failed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                    500, "An unknown error occurred.",
                    "There was a problem with one of the HTTP requests")));
        }
    }

    private void sendRemoveCreditCardCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Credit card successfully removed, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply")));
    }

    @RequestMapping(value = "/creditCard/balance", method = RequestMethod.GET)
    public void processCreditCardBalanceRequest(final Callback<String> callback,
                                                @RequestParam("accountNumber") final String accountNumber) {
        System.out.printf("%s received credit card balance request for accountNumber %s.\n", PREFIX, accountNumber);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        try {
            Double creditCardBalance = findActiveCreditCard(getCreditCardsFromAccountNr(accountNumber)).getBalance();
            sendCreditCardBalanceCallback(creditCardBalance, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Unknown error occurred.")));
        } catch (IncorrectInputException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    e.getMessage(), "The accountNumber used does not have a credit card.")));
        }
    }

    private void sendCreditCardBalanceCallback(final Double creditCardBalance, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Credit card balance request successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", creditCardBalance)));
    }

    @RequestMapping(value = "/refillCards", method = RequestMethod.PUT)
    public void processRefillCardsRequest(final Callback<String> callback,
                                          @RequestParam("date") final String dateJson) {
        System.out.printf("%s Recevied refill credit cards request, refilling..\n", PREFIX);
        LocalDate systemDate = (LocalDate) jsonConverter.fromJson(dateJson, LocalDate.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        List<CreditCard> cardsToRefill = getCreditCardsToRefill();
        refillCreditCards(cardsToRefill, 0L, false, callbackBuilder);
    }

    private List<CreditCard> getCreditCardsToRefill() {
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement getCards = connection.getConnection().prepareStatement(getCreditCardsWithCredit);
            ResultSet cardsWithCredit = getCards.executeQuery();
            List<CreditCard> cardsToRefill = new LinkedList<>();
            while (cardsWithCredit.next()) {
                CreditCard creditCard = new CreditCard();
                creditCard.setCreditCardNumber(cardsWithCredit.getLong("card_number"));
                creditCard.setAccountNumber(cardsWithCredit.getString("account_number"));
                creditCard.setPinCode(cardsWithCredit.getString("pin_code"));
                creditCard.setLimit(cardsWithCredit.getDouble("credit_limit"));
                creditCard.setBalance(cardsWithCredit.getDouble("balance"));
                creditCard.setFee(cardsWithCredit.getDouble("card_fee"));
                creditCard.setActivationDate(cardsWithCredit.getDate("active_from").toLocalDate());
                creditCard.setActive(cardsWithCredit.getBoolean("active"));
                cardsToRefill.add(creditCard);
            }
            return cardsToRefill;
        } catch (SQLException e) {
            e.printStackTrace();
            return new LinkedList<>();
        }
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
