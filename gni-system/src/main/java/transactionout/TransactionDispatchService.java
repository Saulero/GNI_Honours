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
     * Processes transactions from the User and Pin services, sends them to the ledger for processing and then
     * reports the result back to the source of the transaction request.
     * @param callback Used to send the result back to the request source.
     * @param transactionRequestJson Json String containing a Transaction object {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("body") final String transactionRequestJson) {
        Transaction request = jsonConverter.fromJson(transactionRequestJson, Transaction.class);
        System.out.printf("TransactionDispatch: Transaction received, sourceAccount: %s ,destAccount: %s, amount: %f\n",
                            request.getSourceAccountNumber(), request.getDestinationAccountNumber(),
                            request.getTransactionAmount());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doTransactionRequest(transactionRequestJson, callbackBuilder);
    }

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

    private void sendTransactionRequestCallback(final String transactionReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("TransactionDispatch: Successfull transaction, sending back reply.");
        callbackBuilder.build().reply(transactionReplyJson);
    }
}
