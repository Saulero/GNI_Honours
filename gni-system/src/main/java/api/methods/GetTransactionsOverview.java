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
public abstract class GetTransactionsOverview {

    /**
     * Fetches the transaction history of an account, the authToken needs to belong to a user that is authorized to view
     * the transaction history of the iBAN.
     * @param params Parameters of the request (authToken, iBAN, nrOfTransactions).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getTransactionsOverview(final Map<String, Object> params, final ApiBean api) {
        DataRequest request = JSONParser.createJsonDataRequest((String) params.get("iBAN"),
                RequestType.TRANSACTIONHISTORY, 0L);
        MessageWrapper messageWrapper = JSONParser.createMessageWrapper(false, 0, "Request", request);
        messageWrapper.setMethodType(MethodType.GET_TRANSACTION_OVERVIEW);
        System.out.printf("%s Sending transactionOverview request.\n", PREFIX);
        handleDataRequestExceptions(
                messageWrapper, (String) params.get("authToken"), (Long) params.get("nrOfTransactions"), api);
    }
}
