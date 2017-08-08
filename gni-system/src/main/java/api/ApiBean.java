package api;

import com.google.gson.Gson;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.CallbackBuilder;

/**
 * @author Saul
 */
public class ApiBean {

    /** Service object to get the clients and jsonConverter. */
    private final ApiService service;
    /** The callbackBuilder back to the source. */
    private final CallbackBuilder callbackBuilder;
    /** The request id. */
    private final Object id;

    /**
     * Constructor.
     * @param newService The APIService
     * @param newCallbackBuilder The callbackBuilder
     * @param newId The request id
     */
    public ApiBean(final ApiService newService, final CallbackBuilder newCallbackBuilder, final Object newId) {
        this.service = newService;
        this.callbackBuilder = newCallbackBuilder;
        this.id = newId;
    }

    /**
     * Returns the ApiService itself.
     * @return The Service
     */
    public ApiService getService() {
        return service;
    }

    /**
     * Returns the callbackBuilder.
     * @return The callbackBuilder
     */
    public CallbackBuilder getCallbackBuilder() {
        return callbackBuilder;
    }

    /**
     * Returns the request id.
     * @return The request id
     */
    public Object getId() {
        return id;
    }

    /**
     * Returns the connection to the Pin Service.
     * @return The connection
     */
    public HttpClient getPinClient() {
        return service.getPinClient();
    }

    /**
     * Returns the connection to the Pin Service.
     * @return The connection
     */
    public HttpClient getSystemInformationClient() {
        return service.getSystemInformationClient();
    }

    /**
     * Returns the connection to the Pin Service.
     * @return The connection
     */
    public HttpClient getAuthenticationClient() {
        return service.getAuthenticationClient();
    }

    /**
     * Returns the jsonConverter.
     * @return The jsonConverter
     */
    public Gson getJsonConverter() {
        return service.getJsonConverter();
    }
}
