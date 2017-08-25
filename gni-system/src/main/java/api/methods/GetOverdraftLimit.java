package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.DataRequest;
import databeans.MessageWrapper;
import databeans.RequestType;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.ProcessDataRequest.handleDataRequestExceptions;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class GetOverdraftLimit {

    /**
     * Gets current overdraft limit for a certain account.
     * @param params Map containing the parameters of the request (authToken, IBAN).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getOverdraftLimit(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.OVERDRAFTLIMIT, 0L);
        System.out.printf("%s Sending close account request.\n", PREFIX);
        handleDataRequestExceptions(request, (String) params.get("authToken"), 0L, api);
    }
}
