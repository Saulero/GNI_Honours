package api.methods;

import api.ApiBean;
import databeans.DataRequest;
import databeans.RequestType;
import util.JSONParser;

import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.ProcessDataRequest.handleDataRequestExceptions;

/**
 * @author Saul
 */
public class GetUserAccess {

    /**
     * Fetches a list of all the accounts that a user has access to.
     * @param params Parameters of the request (authToken).
     */
    public static void getUserAccess(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest(null, RequestType.CUSTOMERACCESSLIST, 0L);
        System.out.printf("%s Sending UserAccess request.\n", PREFIX);
        handleDataRequestExceptions(request, (String) params.get("authToken"), 0L, api);
    }
}
