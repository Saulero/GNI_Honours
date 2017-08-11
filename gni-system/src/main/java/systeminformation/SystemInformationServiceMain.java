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
     * Starts an instance of the System Information service on localhost:9998.
     * @param args Obligatory arguments
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services")
                        .setPort(9998);

        managedServiceBuilder.addEndpointService(new SystemInformationService(
                9992, "localhost"))
                .getEndpointServerBuilder().build().startServer();

        System.out.println("System Information service started");
    }
}
