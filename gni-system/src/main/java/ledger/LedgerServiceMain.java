package ledger;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * @author noel
 * @version 1
 */
public class LedgerServiceMain {

    public static void main() {
                /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9992); //Defaults to 8080 or environment variable PORT

        /* Start the service. */
        managedServiceBuilder.addEndpointService(new LedgerService()) //Register TodoService
                .getEndpointServerBuilder()
                .build().startServer();

        System.out.println("LedgerService service started");

    }
}
