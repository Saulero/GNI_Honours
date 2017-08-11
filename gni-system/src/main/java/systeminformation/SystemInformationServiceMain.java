package systeminformation;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the System Information Service.
 * @author Noel & Saul
 * @version 1
 */
public final class SystemInformationServiceMain {

    /**
     * Private constructor for utility class.
     */
    private SystemInformationServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the System Information service.
     * @param args sysInfoPort & sysInfoHost
     */
    public static void main(final String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Please specify the correct arguments: [sysInfoPort, sysInfoHost]");
            System.err.println("Shutting down the System Information service.");
        } else {
            final ManagedServiceBuilder managedServiceBuilder =
                    ManagedServiceBuilder.managedServiceBuilder()
                            .setRootURI("/services")
                            .setPort(Integer.parseInt(args[0]));

            managedServiceBuilder.addEndpointService(new SystemInformationService(Integer.parseInt(args[0]), args[1]))
                    .getEndpointServerBuilder().build().startServer();
        }
    }
}
