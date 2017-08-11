package transactionin;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import util.PortScanner;

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
     * Starts an instance of the Transaction Receive service.
     * @param args sysInfoPort & sysInfoHost
     */
    public static void main(final String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Please specify the correct arguments: [sysInfoPort, sysInfoHost]");
            System.err.println("Shutting down the Transaction Receive service.");
        } else {
            int servicePort = PortScanner.getAvailablePort();

            final ManagedServiceBuilder managedServiceBuilder =
                    ManagedServiceBuilder.managedServiceBuilder()
                            .setRootURI("/services")
                            .setPort(servicePort);

            managedServiceBuilder.addEndpointService(new TransactionReceiveService(
                    servicePort, "localhost",
                    Integer.parseInt(args[0]), args[1]))
                    .getEndpointServerBuilder().build().startServer();

            System.out.println("Transaction Receive service started");
        }
    }
}
