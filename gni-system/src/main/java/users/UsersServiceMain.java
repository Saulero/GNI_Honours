package users;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the UsersService.
 * @author Noel
 * @version 1
 */
public final class UsersServiceMain {

    /**
     * Private constructor for utility class.
     */
    private UsersServiceMain() {
    }

    /**
     * Start
     * @param args
     */
    public static void main(final String[] args) {
                /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9991); //Defaults to 8080 or environment variable PORT

        /* Start the service. */
        managedServiceBuilder.addEndpointService(new UsersService(9992, "localhost",
                9993, "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();

        System.out.println("User service started");
    }
}
