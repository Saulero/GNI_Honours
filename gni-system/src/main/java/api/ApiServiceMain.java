package api;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the ApiService.
 * @author Saul
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
     * Starts a Api service on localhost:9997.
     */
    public static void main() {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9997);
        managedServiceBuilder.addEndpointService(new ApiService(9990,
                "localhost", 9995, "localhost", 9998, "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();
        System.out.println("Api service started");
    }
}
