package users;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the UsersService.
 * @author Noel
 * @version 1
 */
final class UsersServiceMain {

    /**
     * Private constructor for utility class.
     */
    private UsersServiceMain() {
        //Not called
    }

    /**
     * Starts a User service on localhost:9991.
     * @param args Not used.
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9991);
        managedServiceBuilder.addEndpointService(new UsersService(9992, "localhost",
                9993, "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();
        System.out.println("User service started");
    }
}
