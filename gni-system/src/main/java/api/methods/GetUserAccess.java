package api.methods;

import api.ApiBean;
import databeans.DataRequest;
import databeans.MessageWrapper;
import databeans.MethodType;
import databeans.RequestType;
import util.JSONParser;

import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.ProcessDataRequest.handleDataRequestExceptions;

/**
 * @author Saul
 */
public abstract class GetUserAccess {

    /**
     * Fetches a list of all the accounts that a user has access to.
     * @param params Parameters of the request (authToken).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getUserAccess(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest(null, RequestType.CUSTOMERACCESSLIST, 0L);
        MessageWrapper messageWrapper = JSONParser.createMessageWrapper(false, 0, "Request", request);
        messageWrapper.setMethodType(MethodType.GET_USER_ACCESS);
        System.out.printf("%s Sending UserAccess request.\n", PREFIX);
        handleDataRequestExceptions(messageWrapper, (String) params.get("authToken"), 0L, api);
    }
}
