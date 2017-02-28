package pin;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import transactionin.TransactionReceiveService;
import transactionout.TransactionDispatchService;

/**
 * Utility class that contains a main method to start up the PinService.
 * @author Noel
 * @version 1
 */
final class PinServiceMain {

    /**
     * Private constructor for utility class.
     */
    private PinServiceMain() {
        //Not called
    }

    /**
     * Starts a Pin service on localhost:9995.
     * @param args Not used.
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9995);
        managedServiceBuilder.addEndpointService(new PinService(9993,
                                                                "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();
        System.out.println("TransactionReceive service started");
    }
}
