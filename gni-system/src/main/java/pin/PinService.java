package pin;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import databeans.PinTransaction;
import ledger.Transaction;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Created by noel on 5-2-17.
 * @author Noel
 * @version 1
 * Handles Pin transactions by verifying the PIN code for the card used and
 * then handles the transaction request accordingly.
 */
@RequestMapping("/pin")
public class PinService {

    private String transactionDispatchHost;
    private int transactionDispatchPort;

    public PinService(final int newTransactionDispatchPort, final String newTransactionDispatchHost) {
        this.transactionDispatchPort = newTransactionDispatchPort;
        this.transactionDispatchHost = newTransactionDispatchHost;
    }

    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processPinTransaction(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        PinTransaction request = gson.fromJson(body, PinTransaction.class);
        System.out.println("PIN: Received new Pin request from a customer.");
        //TODO internal check to confirm pin number
        //TODO check cardnumber
        boolean pinCorrect = true;
        if (pinCorrect) {
            Transaction transaction = JSONParser.createJsonTransaction(-1, request.getSourceAccountNumber(),
                    request.getDestinationAccountNumber(), request.getDestinationAccountHolderName(),
                    "PIN Transaction",
                    request.getTransactionAmount(), false, false);
            HttpClient httpClient = httpClientBuilder().setHost(transactionDispatchHost)
                                    .setPort(transactionDispatchPort)
                    .build();
            httpClient.start();
            CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
            callbackBuilder.withStringCallback(callback);
            httpClient.putFormAsyncWith1Param("/services/transactionDispatch/transaction", "body",
                    gson.toJson(request), (code, contentType, replyBody) -> {
                        if (code == HTTP_OK) {
                            Transaction reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                    .replaceAll("\\\\", ""), Transaction.class);
                            if (reply.isProcessed() && reply.equalsRequest(transaction)) {
                                if (reply.isSuccessful()) {
                                    System.out.println("PIN: Pin transaction was successfull");
                                    callbackBuilder.build().reply(gson.toJson(reply));
                                } else {
                                    callbackBuilder.build().reject("Pin Transaction was unsuccessfull.");
                                }
                            } else {
                                callbackBuilder.build().reject("Pin Transaction couldn't be processed.");
                            }
                        } else {
                            callbackBuilder.build().reject("Couldn't reach transactionDispatch.");
                        }
                    });
        } else {
            callback.reject("Incorrect PIN.");
        }
    }
}
