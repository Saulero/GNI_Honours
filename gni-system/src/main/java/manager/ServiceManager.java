package manager;

import com.google.gson.Gson;
import io.advantageous.qbit.http.client.HttpClient;
import util.DataReply;
import util.DataRequest;
import util.RequestType;
import util.Util;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 4-2-17.
 * @author Noel
 * @version 1
 * Microservices manager, handles the event manager and starts the microservices.
 */
public final class ServiceManager {
    /**
     * Private constructor to satisfy utility class property.
     */
    private ServiceManager() {
        //Not called
    }

    /**
     * Initializes the eventmanager and then starts all services and sets up
     * their listeners.
     * @param args empty argument
     */
    public static void main(final String[] args) {
        //test variables
        String testAccountNumber = "NL52INGB0987890998";
        String testDestinationNumber = "NL52RABO0987890998";

        //Start http client
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(7777).build();
        httpClient.start();
        getTransactionHistory(httpClient, testAccountNumber);
    }

    private void doTransaction() {

    }

    private void makeNewAccount() {

    }

    private void getCustomerInfo() {

    }

    private void getBalance() {

    }

    private static void getTransactionHistory(HttpClient httpClient, String accountNumber) {
        System.out.println("Sending request");
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.TRANSACTIONHISTORY);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/ui/data", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == 200) {
                        System.out.println("received" + body);
                        DataReply reply = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                DataReply.class);
                        System.out.println("Transaction history request successfull, reply: " +
                                            reply.getData());
                    } else {
                        System.out.println("Transaction history request not successfull, body: " + body);
                    }
                });
    }
}
