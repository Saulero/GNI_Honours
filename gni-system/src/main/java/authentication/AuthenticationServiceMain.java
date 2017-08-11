package authentication;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class that contains a main method to start up the AuthenticationService.
 * @author Noel & Saul
 * @version 1
 */
public final class AuthenticationServiceMain {

    /**
     * Private constructor for utility class.
     */
    private AuthenticationServiceMain() {
        //Not called
    }

    /**
     * Starts an instance of the Authentication service on localhost:9996.
     * @param args Obligatory arguments
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services")
                        .setPort(9996);

        managedServiceBuilder.addEndpointService(new AuthenticationService(
                9991, "localhost",
                9995, "localhost"))
                .getEndpointServerBuilder().build().startServer();

        System.out.println("AuthenticationService service started");
    }
}
