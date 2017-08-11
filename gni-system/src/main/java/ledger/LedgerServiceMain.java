package ledger;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the LedgerService.
 * @author Noel & Saul
 * @version 1
 */
public final class LedgerServiceMain {

    /**
     * Private constructor for utility class.
     */
    private LedgerServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the Ledger service on localhost:9992.
     * @param args Obligatory arguments
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services")
                        .setPort(9992);

        managedServiceBuilder.addEndpointService(new LedgerService(
                9998, "localhost"))
                .getEndpointServerBuilder().build().startServer();

        System.out.println("LedgerService service started");
    }
}
