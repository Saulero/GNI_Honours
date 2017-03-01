package transactionout;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import databeans.Transaction;

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
public class TransactionDispatchService {
    /**Port that the Ledger Service can be found on.*/
    private int ledgerPort;
    /**Host that the User service can be found on.*/
    private String ledgerHost;

    /**
     * Constructor.
     * @param newLedgerPort Port the ledger can be found on.
     * @param newLedgerHost Host the ledger can be found on.
     */
    public TransactionDispatchService(final int newLedgerPort, final String newLedgerHost) {
        this.ledgerPort = newLedgerPort;
        this.ledgerHost = newLedgerHost;
    }

    /**
     * Processes transactions from the User and Pin services, sends them to the ledger for processing and then
     * reports the result back to the source of the transaction request.
     * @param callback Used to send the result back to the request source.
     * @param body Json String containing a Transaction object {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, @RequestParam("body") final String body) {
        Gson gson = new Gson();
        Transaction request = gson.fromJson(body, Transaction.class);
        System.out.printf("TransactionDispatch: Transaction received, sourceAccount: %s ,destAccount: %s, amount: %f\n",
                            request.getSourceAccountNumber(), request.getDestinationAccountNumber(),
                            request.getTransactionAmount());
        HttpClient httpClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).build();
        httpClient.start();
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        httpClient.putFormAsyncWith1Param("/services/ledger/transaction/out", "body", gson.toJson(request),
                                        (code, contentType, replyBody) -> {
            if (code == HTTP_OK) {
                Transaction reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                        .replaceAll("\\\\", ""), Transaction.class);
                System.out.println("TransactionDispatch: Received reply from ledger");
                if (reply.isProcessed()) {
                    if (reply.isSuccessful()) {
                        System.out.println("TransactionDispatch: Successfull transaction, sending back reply.");
                        callbackBuilder.build().reply(gson.toJson(reply));
                        //TODO send outgoing transaction.
                    } else {
                        System.out.println("TransactionDispatch: Transaction wasn't successfull, rejecting.");
                        callbackBuilder.build().reject("Unsuccessfull transaction.");
                    }
                } else {
                    System.out.println("TransactionDispatch: Transaction couldnt be processed, rejecting.");
                    callbackBuilder.build().reject("Transaction couldn't be processed.");
                }
            } else {
                callbackBuilder.build().reject("Recieved an error from ledger.");
            }
        });
    }
}
