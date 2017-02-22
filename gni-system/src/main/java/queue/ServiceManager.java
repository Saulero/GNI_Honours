package queue;

import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.admin.ManagedServiceBuilder;
import io.advantageous.qbit.http.client.HttpClient;
import ledger.Ledger;
import ui.UIService;
import users.UserService;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 4-2-17.
 * @author Noel
 * @version 1
 * Microservices manager, handles the event queue and starts the microservices.
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
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(7777).build();
        httpClient.start();
        System.out.println("Sending request");
        httpClient.getAsyncWith1Param("/services/ui/data", "body", "NL123456",
                                     (code, contentType, body) -> {
            if (code == 200) {
                System.out.println("successfull request, body: " + body);
            } else {
                System.out.println("Request not successfull, body: " + body);
            }
        });
    }
}
