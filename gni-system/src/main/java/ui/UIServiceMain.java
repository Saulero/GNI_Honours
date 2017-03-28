package ui;

import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Utility class to start up the UIService.
 * @author Noel
 * @version 1
 */
final class UIServiceMain {

    /**
     * Private constructor for utility class.
     */
    private UIServiceMain() {
        //Not called
    }

    /**
     * Starts a UI service on localhost:9990.
     * @param args Not used.
     */
    public static void main(final String[] args) {
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9990);
        managedServiceBuilder.addEndpointService(new UIService(9996, "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();
        System.out.println("UI service started");
    }
}
