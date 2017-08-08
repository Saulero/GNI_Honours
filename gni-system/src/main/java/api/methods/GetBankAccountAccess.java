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
public class GetBankAccountAccess {

    /**
     * Fetches a list of all users that have access to a specific bankAccount.
     * @param params Parameters of the request (authToken, iBAN).
     */
    public static void getBankAccountAccess(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.ACCOUNTACCESSLIST, 0L);
        System.out.printf("%s Sending BankAccountAccess request.\n", PREFIX);
        handleDataRequestExceptions(request, (String) params.get("authToken"), 0L, api);
    }
}
