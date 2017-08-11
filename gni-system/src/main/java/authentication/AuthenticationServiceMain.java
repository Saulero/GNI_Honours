package authentication;

import io.advantageous.qbit.admin.ManagedServiceBuilder;
import util.PortScanner;

/**
 * Utility class that contains a main method to start up the AuthenticationService.
 * @author Noel & Saul
 * @version 1
 */
public final class AuthenticationServiceMain {

    /**
     * Private constructor for utility class.
     */
    private AuthenticationServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the Authentication service.
     * @param args sysInfoPort & sysInfoHost
     */
    public static void main(final String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Please specify the correct arguments: [sysInfoPort, sysInfoHost]");
            System.err.println("Shutting down the Authentication service.");
        } else {
            int servicePort = PortScanner.getAvailablePort();

            final ManagedServiceBuilder managedServiceBuilder =
                    ManagedServiceBuilder.managedServiceBuilder()
                            .setRootURI("/services")
                            .setPort(servicePort);

            managedServiceBuilder.addEndpointService(new AuthenticationService(
                    servicePort, "localhost",
                    Integer.parseInt(args[0]), args[1]))
                    .getEndpointServerBuilder().build().startServer();
        }
    }
}
