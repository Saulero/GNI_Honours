package transactionout;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import databeans.Transaction;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * Receives outgoing transaction requests.
 * Sends these requests to the ledger for processing.
 * Handles the response from the ledger and sends the transaction to its
 * respective receiving bank.
 */
@RequestMapping("/transactionDispatch")
class TransactionDispatchService {
    /** Connection to the Ledger service. */
    private HttpClient ledgerClient;
    /** Used for Json conversions. */
    private Gson jsonConverter;

    /**
     * Constructor.
     * @param ledgerPort Port the ledger can be found on.
     * @param ledgerHost Host the ledger can be found on.
     */
    TransactionDispatchService(final int ledgerPort, final String ledgerHost) {
        ledgerClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).buildAndStart();
        jsonConverter = new Gson();
    }

    /**
     * Creates a callback builder for the transaction request, and then forwards it to the ledger.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param transactionRequestJson Json String containing a Transaction object that should be executed
     *                               {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("body") final String transactionRequestJson) {
        Transaction request = jsonConverter.fromJson(transactionRequestJson, Transaction.class);
        System.out.printf("TransactionDispatch: Transaction received, sourceAccount: %s ,destAccount: %s, amount: %f\n",
                            request.getSourceAccountNumber(), request.getDestinationAccountNumber(),
                            request.getTransactionAmount());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doTransactionRequest(transactionRequestJson, callbackBuilder);
    }

    /**
     * Forwards a transaction request to the ledger for execution, and processes the reply if successful, sends a
     * rejection to the service that sent the transaction request if the ledger request fails.
     * @param transactionRequestJson Json String representing a transaction that the ledger should execute
     *                               {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final String transactionRequestJson, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/transaction/out", "body",
                transactionRequestJson, (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        processTransactionReply(transactionReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Recieved an error from ledger.");
                    }
                });
    }

    /**
     * Checks if the transaction is processed and successful, if it is forwards the reply to the requesting service,
     * if it is not sends a rejection to the requesting service.
     * @param transactionReplyJson Json String representing a transaction that the ledger tried to execute
     *                             {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void processTransactionReply(final String transactionReplyJson, final CallbackBuilder callbackBuilder) {
        Transaction transactionReply = jsonConverter.fromJson(JSONParser.sanitizeJson(transactionReplyJson),
                                                              Transaction.class);
        if (transactionReply.isProcessed() && transactionReply.isSuccessful()) {
            //TODO send outgoing transaction.
            sendTransactionRequestCallback(transactionReplyJson, callbackBuilder);
        } else {
            callbackBuilder.build().reject("Transaction couldn't be processed.");
        }
    }

    /**
     * Forwards a String representing a transaction that was executed to the service that sent the transaction request.
     * @param transactionReplyJson Json String representing a transaction that the ledger executed {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionRequestCallback(final String transactionReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("TransactionDispatch: Successfull transaction, sending back reply.");
        callbackBuilder.build().reply(transactionReplyJson);
    }
}
