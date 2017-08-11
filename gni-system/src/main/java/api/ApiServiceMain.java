package api;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import util.PortScanner;

/**
 * Utility class that contains a main method to start up the ApiService.
 * @author Saul & Noel
 * @version 1
 */
public final class ApiServiceMain {

    /**
     * Private constructor for utility class.
     */
    private ApiServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the Api service.
     * @param args sysInfoPort & sysInfoHost
     */
    public static void main(final String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Please specify the correct arguments: [sysInfoPort, sysInfoHost]");
            System.err.println("Shutting down the Api service.");
        } else {
            int servicePort = 9997; //PortScanner.getAvailablePort();

            final ManagedServiceBuilder managedServiceBuilder =
                    ManagedServiceBuilder.managedServiceBuilder()
                            .setRootURI("/services")
                            .setPort(servicePort);

            managedServiceBuilder.addEndpointService(new ApiService(
                    servicePort, "localhost",
                    Integer.parseInt(args[0]), args[1]))
                    .getEndpointServerBuilder().build().startServer();
        }
    }
}
