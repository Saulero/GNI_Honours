package pin;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import transactionin.TransactionReceiveService;
import transactionout.TransactionDispatchService;

/**
 * Created by noel on 23-2-17.
 */
public class PinServiceMain {
    public static void main(final String[] args) {
                /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9995); //Defaults to 8080 or environment variable PORT

        /* Start the service. */
        managedServiceBuilder.addEndpointService(new PinService(9993,
                                                                "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();

        System.out.println("TransactionReceive service started");
    }
}
