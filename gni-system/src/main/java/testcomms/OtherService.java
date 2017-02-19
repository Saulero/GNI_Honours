package testcomms;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.http.client.HttpClient;
import util.DataReply;

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

                /* Using Async support with lambda. */
        httpClient.getAsync("/v1/todo-service/todo", (code, contentType, body) -> {
            if (code == 200) { processReply(body); } else { System.out.println("Request failed"); }
        });
    }

    private static void processReply(String jsonString) {
        Gson gson = new Gson();
        DataReply reply = gson.fromJson(jsonString.substring(1, jsonString.length() - 1).replaceAll("\\\\", ""),
                                         DataReply.class);
        System.out.println("Received new reply" + reply.getAccountNumber() + " " + reply.getData());
    }
}
