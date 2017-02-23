package ui;

import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.admin.ManagedServiceBuilder;

/**
 * Created by noel on 21-2-17.
 */
public class UIServiceMain {

    public static void main(final String[] args) {
                /* Create the ManagedServiceBuilder which manages a clean shutdown, health, stats, etc. */
        final ManagedServiceBuilder managedServiceBuilder =
                ManagedServiceBuilder.managedServiceBuilder()
                        .setRootURI("/services") //Defaults to services
                        .setPort(9990); //Defaults to 8080 or environment variable PORT

        /* Start the service. */
        managedServiceBuilder.addEndpointService(new UIService(9991, "localhost"))
                .getEndpointServerBuilder()
                .build().startServer();

        System.out.println("UI service started");
    }
}
