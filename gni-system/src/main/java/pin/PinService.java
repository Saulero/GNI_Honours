package pin;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;
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
    private static final String prefix = "[PIN]                 :";

    PinService(final int transactionDispatchPort, final String transactionDispatchHost) {
        transactionDispatchClient = httpClientBuilder().setHost(transactionDispatchHost)
                                                        .setPort(transactionDispatchPort).buildAndStart();
        databaseConnectionPool = new ConnectionPool();
        this.jsonConverter = new Gson();
    }

    /**
     * Converts in incoming pintransaction request to a normal transaction request and then sends this request to
     * the exception handler to check the pin combination and execute the transaction.
     * @param callback Used to send a reply to the request source.
     * @param pinTransactionRequestJson Json string representing a pin transaction request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processPinTransaction(final Callback<String> callback,
                                      final @RequestParam("request") String pinTransactionRequestJson) {
        PinTransaction request = jsonConverter.fromJson(pinTransactionRequestJson, PinTransaction.class);
        System.out.printf("%s Received new Pin request from a customer.\n", prefix);
        Transaction transaction = JSONParser.createJsonTransaction(-1, request.getSourceAccountNumber(),
                request.getDestinationAccountNumber(), request.getDestinationAccountHolderName(),
                "PIN Transaction card #" + request.getCardNumber(),
                request.getTransactionAmount(), false, false);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinExceptions(transaction, request.getCardNumber(), request.getPinCode(), callbackBuilder);

    }

    /**
     * Checks if the pincode matches the card number, and fetches the customerId of the owner of the card, then
     * forwards the transaction request to the TransactionDispatch service.
     * @param transaction Transaction to be executed.
     * @param cardNumber Card number of the card used in the transaction.
     * @param pinCode Pin code entered during the transaction request.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void handlePinExceptions(final Transaction transaction, final String cardNumber, final String pinCode,
                                     final CallbackBuilder callbackBuilder) {
        try {
            Long customerId = getCustomerIdFromPinCombination(cardNumber, pinCode);
            doTransactionRequest(transaction, customerId, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("Something went wrong when connecting to the pin database.");
        } catch (IncorrectPinException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("Incorrect PIN.");
        }
    }

    /**
     * Fetches the customerId of the customer the card belongs to if the card number and pin code are correct and
     * belong to a customer, otherwise throws an IncorrectPinException.
     * @param cardNumber Card number of the card used.
     * @param pinCode Pin code entered that should belong to the card used.
     * @return CustomerId of the owner of the card.
     * @throws SQLException Thrown when a database issue occurs.
     * @throws IncorrectPinException Thrown when the cardNumber and pinCode don't match.
     */
    private Long getCustomerIdFromPinCombination(final String cardNumber, final String pinCode) throws SQLException,
                                                IncorrectPinException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCustomerId = databaseConnection.getConnection().prepareStatement(SQLStatements
                                                                                .getCustomerIdFromPinCombination);
        getCustomerId.setString(1, cardNumber);
        getCustomerId.setString(2, pinCode);
        ResultSet fetchedCustomerId = getCustomerId.executeQuery();
        if (fetchedCustomerId.next()) {
            Long customerId = fetchedCustomerId.getLong(1);
            getCustomerId.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return customerId;
        } else {
            getCustomerId.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            throw new IncorrectPinException("There does not exist a customer with this cardnumber & pincode.");
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
                        System.out.printf("%s Transaction request failed, sending rejection.\n", prefix);
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
                System.out.printf("%s Pin transaction was successfull, sending callback.\n", prefix);
                callbackBuilder.build().reply(jsonConverter.toJson(reply));
            } else {
                System.out.printf("%s Pin transaction was unsuccessfull, sending rejection.\n", prefix);
                callbackBuilder.build().reject("PIN: Pin Transaction was unsuccessfull.");
            }
        } else {
            System.out.printf("%s Pin transaction couldn't be processed, sending rejection.\n", prefix);
            callbackBuilder.build().reject("PIN: Pin Transaction couldn't be processed.");
        }
    }

    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void addPinCard(final Callback<String> callback, final @RequestParam("customerId") String customerId) {

    }

    private String getNextAvailableCardNumber() throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getHighestCardNumber = databaseConnection.getConnection()
                                                            .prepareStatement(SQLStatements.getHighestCardNumber);
        ResultSet highestCardNumber = getHighestCardNumber.executeQuery();
        if (highestCardNumber.next()) {
            String cardNumber = highestCardNumber.getString(2);
            getHighestCardNumber.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return String.format("%04d", Integer.parseInt(cardNumber) + 1);
        } else {
            //There are no cards in the system
            getHighestCardNumber.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return "0000";
        }
    }
}
