package transactionin;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Created by noel on 23-2-17.
 * @author Noel
 * @version 1
 */
public class TransactionReceiveServiceMain {
    public static void main(final String[] args) {
                /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9994); //Defaults to 8080 or environment variable PORT

        /* Start the service. */
        managedServiceBuilder.addEndpointService(new TransactionReceiveService(9992,
                "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();

        System.out.println("TransactionReceive service started");
    }
}
