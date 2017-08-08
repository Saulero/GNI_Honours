package api.methods;

import api.ApiBean;
import databeans.DataRequest;
import databeans.RequestType;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.ProcessDataRequest.handleDataRequestExceptions;

/**
 * @author Saul
 */
public class GetBalance {

    /**
     * Fetches the balance for a bank account, the authToken needs to belong to a user that is authorized to view
     * the balance of the iBAN.
     * @param params Parameters of the request (authToken, iBAN).
     */
    public static void getBalance(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = new DataRequest((String) params.get("iBAN"), RequestType.BALANCE, 0L);
        System.out.printf("%s Sending getBalance request.\n", PREFIX);
        handleDataRequestExceptions(request, (String) params.get("authToken"), api);
    }
}
