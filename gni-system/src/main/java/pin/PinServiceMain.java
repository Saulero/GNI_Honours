package pin;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the PinService.
 * @author Noel
 * @version 1
 */
public final class PinServiceMain {

    /**
     * Private constructor for utility class.
     */
    private PinServiceMain() {
        //Not called
    }

    /**
     * Starts a Pin service on localhost:9995.
     */
    public static void main() {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9995);
        managedServiceBuilder.addEndpointService(new PinService(9993,
                "localhost", 9994, "localhost",
                9998, "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();
        System.out.println("Pin service started");
    }
}
