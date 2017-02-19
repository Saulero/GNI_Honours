package testcomms;

import com.google.gson.Gson;
import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.http.client.HttpClient;
import util.DataReply;
import util.DataRequest;
import util.RequestType;
import util.Util;

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
        String req = gson.toJson(request);
        String tosend = "{\"accountNumber\":\"123456\",\"type\":\"BALANCE\"}";
        System.out.println("sending " + tosend);
        Sys.sleep(100);
        httpClient.getAsyncWith1Param("/v1/todo-service/todo", "body", tosend,
                (code, contentType, body) -> {
                            if (code == 200) {processReply(body);} else { System.out.println("Request failed"); } });
    }

    private static void processReply(String jsonReply) {
        Gson gson = new Gson();
        DataReply reply = gson.fromJson(jsonReply.substring(1, jsonReply.length() - 1).replaceAll("\\\\", ""),
                                         DataReply.class);
        System.out.println("Received new reply " + reply.getAccountNumber() + " " + reply.getData());
    }
}
