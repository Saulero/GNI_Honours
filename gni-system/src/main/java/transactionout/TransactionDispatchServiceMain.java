package transactionout;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the TransactionDispatchService.
 * @author Noel & Saul
 * @version 1
 */
public final class TransactionDispatchServiceMain {

    /**
     * Private constructor for utility class.
     */
    private TransactionDispatchServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the Transaction Dispatch service on localhost:9993.
     * @param args Obligatory arguments
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services")
                        .setPort(9993);

        managedServiceBuilder.addEndpointService(new TransactionDispatchService(
                9992, "localhost"))
                .getEndpointServerBuilder().build().startServer();

        System.out.println("Transaction Dispatch service started");
    }
}
