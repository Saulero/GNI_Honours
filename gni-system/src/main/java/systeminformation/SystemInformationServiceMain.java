package systeminformation;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the System Information Service.
 * @author Noel
 * @version 1
 */
public class SystemInformationServiceMain {

    /**
     * Private constructor for utility class.
     */
    private SystemInformationServiceMain() {
        //Not called
    }

    /**
     * Starts a System Information service on localhost:9998.
     */
    public static void main() {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9998);
        managedServiceBuilder.addEndpointService(new SystemInformationService())
                .getEndpointServerBuilder()
                .build().startServer();
        System.out.println("System Information service started");
    }
}
