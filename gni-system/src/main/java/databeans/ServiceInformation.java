package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public class ServiceInformation implements Serializable {

    private int servicePort;
    private String serviceHost;
    private ServiceType serviceType;

    public ServiceInformation(final int port, final String host, final ServiceType type) {
        this.servicePort = port;
        this.serviceHost = host;
        this.serviceType = type;
    }

    public int getServicePort() {
        return servicePort;
    }

    public String getServiceHost() {
        return serviceHost;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }
}
