package api;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

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
     * Starts an instance of the Api service on localhost:9997.
     * @param args Obligatory arguments
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services")
                        .setPort(9997);

        managedServiceBuilder.addEndpointService(new ApiService(
                9996, "localhost",
                9995, "localhost",
                9998, "localhost"))
                .getEndpointServerBuilder().build().startServer();

        System.out.println("Api service started");
    }
}
