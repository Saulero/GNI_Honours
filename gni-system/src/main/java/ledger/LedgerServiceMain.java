package ledger;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import util.PortScanner;

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
     * Starts an instance of the Ledger service.
     * @param args sysInfoPort & sysInfoHost
     */
    public static void main(final String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Please specify the correct arguments: [sysInfoPort, sysInfoHost]");
            System.err.println("Shutting down the Ledger service.");
        } else {
            int servicePort = PortScanner.getAvailablePort();

            final ManagedServiceBuilder managedServiceBuilder =
                    ManagedServiceBuilder.managedServiceBuilder()
                            .setRootURI("/services")
                            .setPort(servicePort);

            managedServiceBuilder.addEndpointService(new LedgerService(
                    servicePort, "localhost",
                    Integer.parseInt(args[0]), args[1]))
                    .getEndpointServerBuilder().build().startServer();
        }
    }
}
