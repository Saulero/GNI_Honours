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
public abstract class GetBankAccountAccess {

    /**
     * Fetches a list of all users that have access to a specific bankAccount.
     * @param params Parameters of the request (authToken, iBAN).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getBankAccountAccess(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.ACCOUNTACCESSLIST, 0L);
        MessageWrapper messageWrapper = JSONParser.createMessageWrapper(false, 0, "Request", request);
        messageWrapper.setMethodType(MethodType.GET_BANK_ACCOUNT_ACCESS);
        System.out.printf("%s Sending BankAccountAccess request.\n", PREFIX);
        handleDataRequestExceptions(messageWrapper, (String) params.get("authToken"), 0L, api);
    }
}
