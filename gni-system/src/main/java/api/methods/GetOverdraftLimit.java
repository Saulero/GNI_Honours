package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.DataRequest;
import databeans.MessageWrapper;
import databeans.MethodType;
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
public abstract class GetOverdraftLimit {

    /**
     * Gets current overdraft limit for a certain account.
     * @param params Map containing the parameters of the request (authToken, IBAN).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getOverdraftLimit(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.OVERDRAFTLIMIT, 0L);
        MessageWrapper messageWrapper = JSONParser.createMessageWrapper(false, 0, "Request", request);
        messageWrapper.setMethodType(MethodType.GET_OVERDRAFT_LIMIT);
        System.out.printf("%s Sending close account request.\n", PREFIX);
        handleDataRequestExceptions(messageWrapper, (String) params.get("authToken"), 0L, api);
    }
}
