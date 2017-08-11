package transactionin;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the TransactionReceiveService.
 * @author Noel & Saul
 * @version 1
 */
public final class TransactionReceiveServiceMain {

    /**
     * Private constructor for utility class.
     */
    private TransactionReceiveServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the Transaction Receive service on localhost:9994.
     * @param args Obligatory arguments
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services")
                        .setPort(9994);

        managedServiceBuilder.addEndpointService(new TransactionReceiveService(
                9992, "localhost"))
                .getEndpointServerBuilder().build().startServer();

        System.out.println("TransactionReceive service started");
    }
}
