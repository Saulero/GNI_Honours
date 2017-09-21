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
public abstract class GetBalance {

    /**
     * Fetches the balance for a bank account, the authToken needs to belong to a user that is authorized to view
     * the balance of the iBAN.
     * @param params Parameters of the request (authToken, iBAN).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getBalance(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = new DataRequest((String) params.get("iBAN"), RequestType.BALANCE, 0L);
        MessageWrapper messageWrapper = JSONParser.createMessageWrapper(false, 0, "Request", request);
        messageWrapper.setMethodType(MethodType.GET_BALANCE);
        System.out.printf("%s Sending getBalance request.\n", PREFIX);
        handleDataRequestExceptions(messageWrapper, (String) params.get("authToken"), 0L, api);
    }
}
