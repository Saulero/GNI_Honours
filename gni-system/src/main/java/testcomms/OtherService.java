package testcomms;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.http.request.HttpTextReceiver;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 18-2-17.
 */
@RequestMapping("/other")
public class OtherService {

    @RequestMapping(value = "/req", method = RequestMethod.GET)
    public void req(final Callback<String> callback, @RequestParam("body") final String body) {
                /* Setup an httpClient. */
        HttpClient httpClient = httpClientBuilder()
                .setHost("localhost").setPort(8888).build();
        httpClient.start();
        DataRequest request = Util.createJsonRequest("123456", RequestType.BALANCE);
        Gson gson = new Gson();
        System.out.println("Received " + body);
        System.out.println("requesting for: " + request);
        String json = gson.toJson(request);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        httpClient.getAsyncWith1Param("/services/todo-service/todo", "body", json,
                                        (code, contentType, replyBody) -> {
            if (code == 200) {
                System.out.println("processing");
                processReply(replyBody, callbackBuilder.build());
            } else {
                System.out.println("Request failed in other");
                callbackBuilder.build().reject("Request failed");
            }
        });
    }

    @RequestMapping(value = "/post", method = RequestMethod.POST)
    public void post(Callback<String> callback, @RequestParam("body") final String body) {
        Gson gson = new Gson();
        System.out.println("in post");
                /* Setup an httpClient. */
        HttpClient httpClient = httpClientBuilder()
                .setHost("localhost").setPort(8888).build();
        httpClient.start();
        Transaction transaction = Util.createJsonTransaction(13245678, "324567",
                "12345678", "De Vries",
                222.22, false, false);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        httpClient.putFormAsyncWith1Param("/v1/todo-service/todo", "body", gson.toJson(transaction),
                (code, contentType, replyBody) -> {
                    if (code == 200) {
                        processTransaction(replyBody, callbackBuilder.build());
                    } else {
                        System.out.println("Transaction failed");
                    }
                });
    }


    private static void processReply(String jsonReply, Callback<String> callback) {
        Gson gson = new Gson();
        DataReply reply = gson.fromJson(jsonReply.substring(1, jsonReply.length() - 1).replaceAll("\\\\", ""),
                                         DataReply.class);
        System.out.println("Received new reply " + reply.getAccountNumber() + " " + reply.getData());
        callback.reply("awesome");
    }

    private static void processTransaction(String jsonReply, Callback<String> callback) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(jsonReply.substring(1, jsonReply.length() - 1).replaceAll("\\\\", ""),
                                                Transaction.class);
        System.out.println("Received new Transconfirm " + transaction.getTransactionID() + " " +
                            transaction.isSuccessfull());
        callback.reply("ayylmao");
    }
}
