package util;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @author Saul
 */
public final class PortScanner {

    /**
     * Private constructor for utility class.
     */
    private PortScanner() {
        //Not called
    }

    /**
     * Opens and closes a socket on a random available port, then closes the socket and returns the port.
     * @return The port available for use
     */
    public static int getAvailablePort() {
        int availablePort = -1;

        try {
            ServerSocket s = new ServerSocket(0);
            availablePort = s.getLocalPort();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return availablePort;
    }
}
