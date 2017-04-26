package pin;

import com.google.gson.Gson;
import databeans.PinTransaction;
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
 * Created by noel on 5-2-17.
 * @author Noel
 * @version 1
 * Handles Pin transactions by verifying the PIN code for the card used and
 * then handles the transaction request accordingly.
 */
@RequestMapping("/pin")
class PinService {
    /** Connection to the Transaction Dispatch service.*/
    private HttpClient transactionDispatchClient;
    /** Prefix used when printing to indicate the message is coming from the PIN Service. */
    private static final String prefix = "[PIN]                 :";

    PinService(final int transactionDispatchPort, final String transactionDispatchHost) {
        transactionDispatchClient = httpClientBuilder().setHost(transactionDispatchHost)
                                                        .setPort(transactionDispatchPort).buildAndStart();
    }

    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processPinTransaction(final Callback<String> callback, final @RequestParam("request") String body) {
        Gson gson = new Gson();
        PinTransaction request = gson.fromJson(body, PinTransaction.class);
        System.out.printf("%s Received new Pin request from a customer.\n", prefix);
        Transaction transaction = JSONParser.createJsonTransaction(-1, request.getSourceAccountNumber(),
                request.getDestinationAccountNumber(), request.getDestinationAccountHolderName(),
                "PIN Transaction card #" + request.getCardNumber(),
                request.getTransactionAmount(), false, false);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        transactionDispatchClient.putFormAsyncWith1Param("/services/transactionDispatch/transaction",
                "request", gson.toJson(transaction), (code, contentType, replyBody) -> {
            if (code == HTTP_OK) {
                Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(replyBody), Transaction.class);
                if (reply.isProcessed() && reply.equalsRequest(transaction)) {
                    if (reply.isSuccessful()) {
                        System.out.printf("%s Pin transaction was successfull, sending callback.\n", prefix);
                        callbackBuilder.build().reply(gson.toJson(reply));
                    } else {
                        System.out.printf("%s Pin transaction was unsuccessfull, sending rejection.\n", prefix);
                        callbackBuilder.build().reject("PIN: Pin Transaction was unsuccessfull.");
                    }
                } else {
                    System.out.printf("%s Pin transaction couldn't be processed, sending rejection.\n", prefix);
                    callbackBuilder.build().reject("PIN: Pin Transaction couldn't be processed.");
                }
            } else {
                System.out.printf("%s Failed to reach transactionDispatch, sending rejection.\n", prefix);
                callbackBuilder.build().reject("PIN: Couldn't reach transactionDispatch.");
            }
        });
    }
}
