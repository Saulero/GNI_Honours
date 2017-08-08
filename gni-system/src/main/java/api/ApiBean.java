package api;

import com.google.gson.Gson;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.CallbackBuilder;

/**
 * @author Saul
 */
public class ApiBean {

    private final ApiService service;
    private final CallbackBuilder callbackBuilder;
    private final Object id;

    public ApiBean(ApiService service, CallbackBuilder callbackBuilder, Object id) {
        this.service = service;
        this.callbackBuilder = callbackBuilder;
        this.id = id;
    }

    public ApiService getService() {
        return service;
    }

    public CallbackBuilder getCallbackBuilder() {
        return callbackBuilder;
    }

    public Object getId() {
        return id;
    }

    public HttpClient getPinClient() {
        return service.getPinClient();
    }

    public HttpClient getSystemInformationClient() {
        return service.getSystemInformationClient();
    }

    public HttpClient getAuthenticationClient() {
        return service.getAuthenticationClient();
    }

    public Gson getJsonConverter() {
        return service.getJsonConverter();
    }
}
