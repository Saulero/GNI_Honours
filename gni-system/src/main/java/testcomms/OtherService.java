package testcomms;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.http.client.HttpClient;
import util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 18-2-17.
 */
@RequestMapping("/other")
public class OtherService {

    public static void main(String[] args) {
                /* Setup an httpClient. */
        HttpClient httpClient = httpClientBuilder()
                .setHost("localhost").setPort(8888).build();
        httpClient.start();
        DataRequest request = Util.createJsonRequest("123456", RequestType.BALANCE);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/v1/todo-service/todo", "body", gson.toJson(request),
                                        (code, contentType, body) -> {
            if (code == 200) {
                processReply(body);
            } else {
                System.out.println("Request failed");
            }
        });
        Transaction transaction = Util.createJsonTransaction(13245678, "324567",
                                        "12345678", "De Vries",
                                        222.22, false, false);
        httpClient.putFormAsyncWith1Param("/v1/todo-service/todo", "body", gson.toJson(transaction),
                                            (code, contentType, body) -> {
                    if (code == 200) {
                        processTransaction(body);
                    } else {
                        System.out.println("Transaction failed");
                    }
        });
    }

    private static void processReply(String jsonReply) {
        Gson gson = new Gson();
        DataReply reply = gson.fromJson(jsonReply.substring(1, jsonReply.length() - 1).replaceAll("\\\\", ""),
                                         DataReply.class);
        System.out.println("Received new reply " + reply.getAccountNumber() + " " + reply.getData());
    }

    private static void processTransaction(String jsonReply) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(jsonReply.substring(1, jsonReply.length() - 1).replaceAll("\\\\", ""),
                                                Transaction.class);
        System.out.println("Received new Transconfirm " + transaction.getTransactionID() + " " +
                            transaction.isSuccessfull());
    }
}
