package transactionin;

import com.google.gson.Gson;
import databeans.MessageWrapper;
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
class TransactionReceiveService {
    /** Connection to the Ledger service.*/
    private HttpClient ledgerClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Transaction Receive Service. */
    private static final String prefix = "[TransactionReceive]  :";

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
                                           final @RequestParam("request") String transactionRequestJson) {
        System.out.printf("%s Received incoming transaction request.\n", prefix);
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
        ledgerClient.putFormAsyncWith1Param("/services/ledger/transaction/in", "request",
                transactionRequestJson, (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            processIncomingTransactionReply((Transaction) messageWrapper.getData(), transactionReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(transactionReplyJson);
                        }
                    } else {
                        System.out.printf("%s Received a rejection from ledger, sending rejection.\n", prefix);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests4")));
                    }
                });
    }

    private void processIncomingTransactionReply(final Transaction transaction, final String transactionReplyJson,
                                                 final CallbackBuilder callbackBuilder) {
        if (transaction.isProcessed() && transaction.isSuccessful()) {
            sendIncomingTransactionRequestCallback(transactionReplyJson, callbackBuilder);
            //TODO send reply to external bank.
        } else {
            System.out.printf("%s Transaction unsuccessful, sending rejection.\n", prefix);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.")));
        }
    }

    private void sendIncomingTransactionRequestCallback(final String transactionReplyJson,
                                                        final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully processed incoming transaction, sending callback.\n", prefix);
        callbackBuilder.build().reply(transactionReplyJson);
    }
}
