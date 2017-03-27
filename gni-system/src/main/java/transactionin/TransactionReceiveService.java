package transactionin;

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
 * @version 2
 * Receives transaction requests from external banks, send them to the ledger
 * for processing, and sends the confirmation/failure back to the external bank.
 */
@RequestMapping("/transactionReceive")
public class TransactionReceiveService {
    /** Connection to the Ledger service.*/
    private HttpClient ledgerClient;
    /** Used for json conversions. */
    private Gson jsonConverter;

    /**
     * Constructor.
     * @param ledgerPort Port the LedgerService can be found on.
     * @param ledgerHost Host the ledger can be found on.
     */
    public TransactionReceiveService(final int ledgerPort, final String ledgerHost) {
        ledgerClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).buildAndStart();
        jsonConverter = new Gson();
    }

    /**
     * Processes transactions that come from external banks by checking if the destination is a GNIB accountNumber
     * and then executing the transaction. Reports the result back to the request source.
     * @param callback Used to send a reply back to the external bank.
     * @param transactionRequestJson Json String representing an incoming transaction.
     */
    //TODO might need reworking when it is clear how external transactions will be sent
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processIncomingTransaction(final Callback<String> callback,
                                           final @RequestParam("body") String transactionRequestJson) {
        System.out.println("TransactionReceive: Received incoming transaction request.");
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doIncomingTransactionRequest(transactionRequestJson, callbackBuilder);
    }

    /**
     * Sends a transaction request to the LedgerService for executing and then processes the reply and reports the result
     * back to the request source.
     * @param callbackBuilder Used to send the result back to the bank that requested the transaction.
     */
    private void doIncomingTransactionRequest(final String transactionRequestJson,
                                              final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/transaction/in", "body",
                transactionRequestJson, (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        processIncomingTransactionReply(transactionReplyJson, callbackBuilder);
                    } else {
                        //TODO send unsuccessfull reply instead of rejection
                        callbackBuilder.build().reject("Recieved an error from ledger.");
                    }
                });
    }

    private void processIncomingTransactionReply(final String transactionReplyJson,
                                                 final CallbackBuilder callbackBuilder) {
        Transaction reply = jsonConverter.fromJson(JSONParser.sanitizeJson(transactionReplyJson), Transaction.class);
        if (reply.isProcessed() && reply.isSuccessful()) {
            sendIncomingTransactionRequestCallback(transactionReplyJson, callbackBuilder);
            //TODO send reply to external bank.
        } else {
            callbackBuilder.build().reject("Transaction couldn't be processed.");
        }
    }

    private void sendIncomingTransactionRequestCallback(final String transactionReplyJson,
                                                        final CallbackBuilder callbackBuilder) {
        System.out.println("TransactionReceive: Successfully processed incoming transaction");
        callbackBuilder.build().reply(JSONParser.sanitizeJson(transactionReplyJson));
    }
}
