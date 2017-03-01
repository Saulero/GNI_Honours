package transactionin;

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
 * Receives transaction requests from external banks, send them to the ledger
 * for processing, and sends the confirmation/failure back to the external bank.
 */
@RequestMapping("/transactionReceive")
public class TransactionReceiveService {
    /**Port that the Ledger Service can be found on.*/
    private int ledgerPort;
    /**Host that the User service can be found on.*/
    private String ledgerHost;

    /**
     * Constructor.
     * @param newLedgerPort Port the LedgerService can be found on.
     * @param newLedgerHost Host the ledger can be found on.
     */
    public TransactionReceiveService(final int newLedgerPort, final String newLedgerHost) {
        this.ledgerPort = newLedgerPort;
        this.ledgerHost = newLedgerHost;
    }

    /**
     * Processes transactions that come from external banks by checking if the destination is a GNIB accountNumber
     * and then executing the transaction. Reports the result back to the request source.
     * @param callback Used to send a reply back to the external bank.
     * @param body Json String representing an external transaction.
     */
    //TODO might need reworking when it is clear how external transactions will be sent
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processIncomingTransaction(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction request = gson.fromJson(body, Transaction.class);
        System.out.println("TransactionReceive: Received transaction request from external bank");
        HttpClient httpClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).build();
        httpClient.start();
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        doTransaction(httpClient, gson, request, callbackBuilder);
    }

    /**
     * Sends a transaction request to the LedgerService for executing and then processes the reply and reports the result
     * back to the request source.
     * @param httpClient HttpClient used to communicate with the LedgerService.
     * @param gson Used for Json conversions.
     * @param request Transaction object containing the transaction requested by an external bank.
     * @param callbackBuilder Used to send the result back to the bank that requested the transaction.
     */
    private void doTransaction(final HttpClient httpClient, final Gson gson, final Transaction request,
                               final CallbackBuilder callbackBuilder) {
        httpClient.putFormAsyncWith1Param("/services/ledger/transaction/in", "body", gson.toJson(request),
                (code, contentType, replyBody) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                .replaceAll("\\\\", ""), Transaction.class);
                        if (reply.isProcessed()) {
                            if (reply.isSuccessful()) {
                                System.out.println("TransactionReceive: Successfully processed external transaction");
                                callbackBuilder.build().reply(gson.toJson(reply));
                                //TODO send reply to external bank.
                            } else {
                                System.out.println("TransactionReceive: External transaction wasn't successfull,"
                                                    + " rejecting.");
                                //TODO send unsuccessfull reply instead of rejection
                                callbackBuilder.build().reject("Unsuccessfull external transaction.");
                            }
                        } else {
                            System.out.println("External transaction couldnt be processed, rejecting.");
                            //TODO send unsuccessfull reply instead of rejection
                            callbackBuilder.build().reject("Transaction couldn't be processed.");
                        }
                    } else {
                        //TODO send unsuccessfull reply instead of rejection
                        callbackBuilder.build().reject("Recieved an error from ledger.");
                    }
                });
    }
}
