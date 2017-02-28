package users;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Created by noel on 21-2-17.
 */
public class UsersServiceMain {

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
