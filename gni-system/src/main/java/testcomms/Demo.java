package testcomms;

import com.google.gson.Gson;
import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.http.client.HttpClient;
import util.DataRequest;
import util.RequestType;
import util.Util;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 21-2-17.
 */
public class Demo {
    /*
    todoService is on port 8888, other is on port 9999
    run both services then check some stuff.
     */
    public static void main(final String[] args) {
        try {
            /*TodoServiceMain todo = new TodoServiceMain();
            OtherServiceMain other = new OtherServiceMain();
            Thread todoThread = new Thread(todo);
            todoThread.start();
            Sys.sleep(2000);
            Thread otherThread = new Thread(other);
            otherThread.start();
            Sys.sleep(1000);*/
            System.out.println("Starting demo...");
        HttpClient httpClient = httpClientBuilder()
                .setHost("localhost").setPort(9999).build();
        httpClient.start();
        DataRequest request = Util.createJsonRequest("123456", RequestType.BALANCE);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/other/req", "body", gson.toJson(request),
                                        (code, contentType, body) -> {
            if (code == 200) {
                System.out.println("success bby" + body);
            } else {
                System.out.println("Request failed in demo");
                System.out.println(body);
            }
        });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
