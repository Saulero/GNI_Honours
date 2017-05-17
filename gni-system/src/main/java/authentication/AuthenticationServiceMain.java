package authentication;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * @author Noel
 * @version 1
 */
public class AuthenticationServiceMain {

    public static void main() {
                /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9996); //Defaults to 8080 or environment variable PORT

        /* Start the service. */
        managedServiceBuilder.addEndpointService(new AuthenticationService(9991, "localhost",
                                                9995, "localhost")) //Register TodoService
                .getEndpointServerBuilder()
                .build().startServer();

        System.out.println("AuthenticationService service started");

    }
}
