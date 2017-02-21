package queue;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import ui.UIService;
import users.UserService;

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

        /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(8888); //Defaults to 8080 or environment variable PORT


        /* Start the service. */
        managedServiceBuilder.addEndpointService(new UIService()).addEndpointService(new UserService()) //Register services
                .getEndpointServerBuilder()
                .build().startServer();

        /* Start the admin builder which exposes health end-points and swagger meta data. */
        managedServiceBuilder.getAdminBuilder().build().startServer();

        System.out.println("Todo Server and Admin Server started");

        //Emulate user using the uiService
        //TODO move Service calls to the services themselves.
        // Does not work at the moment because there is no code to call the
        // methods.

        /*System.out.println("Manager: Creating customer");
        uiService.createCustomer("freek", "de wilde",
                                "NL52INGB0987890998");
        sleep(200);

        System.out.println("Manager: Requesting customer info");
        uiService.requestCustomerData(testAccountNumber);
        sleep(200);

        System.out.println("Manager: Requesting customer balance");
        uiService.requestBalance(testAccountNumber);
        sleep(200);

        System.out.println("Manager: Requesting transaction history");
        uiService.requestTransactionHistory(testAccountNumber);
        sleep(200);

        System.out.println("Manager: Creating transaction");
        Transaction transaction = new Transaction(112,
                                        testAccountNumber,
                                        testDestinationNumber,
                                        "de wilde",
                                        250);
        eventManager.send(TRANSACTION_REQUEST_CHANNEL, transaction);
        sleep(200);

        System.out.println("Manager: Creating transaction");
        Transaction transaction2 = new Transaction(113,
                                        testAccountNumber,
                                        testDestinationNumber,
                                        "de wilde",
                                        250);
        eventManager.send(TRANSACTION_REQUEST_CHANNEL, transaction2);
        sleep(200);

        System.out.println("Manager: Requesting customer balance");
        uiService.requestBalance(testAccountNumber);
        sleep(200);

        //Test method.
        System.out.println("Manager: Printing ledger:");
        ledger.printLedger();*/
    }
}
