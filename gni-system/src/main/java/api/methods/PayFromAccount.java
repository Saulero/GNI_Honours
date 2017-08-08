package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.PinTransaction;
import databeans.Transaction;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class PayFromAccount {

    /**
     * A money transfer between accounts by use of a pinCard, the user doing the transaction needs to use a pinCard
     * linked to the sourceAccount.
     * @param params Parameters of the request (sourceIBAN, targetIBAN, pinCard, pinCode, amount).
     */
    public static void payFromAccount(final Map<String, Object> params, final ApiBean api) {
        PinTransaction pin = JSONParser.createJsonPinTransaction((String) params.get("sourceIBAN"),
                (String) params.get("targetIBAN"), "", (String) params.get("pinCode"),
                Long.parseLong((String) params.get("pinCard")), (Double) params.get("amount"), false);
        System.out.printf("%s Sending pin transaction.\n", PREFIX);
        api.getPinClient().putFormAsyncWith1Param("/services/pin/transaction",
                "request", api.getJsonConverter().toJson(pin), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Transaction reply = (Transaction) messageWrapper.getData();
                            if (reply.isSuccessful() && reply.isProcessed()) {
                                System.out.printf("%s Pin transaction successful.\n\n\n", PREFIX);
                                Map<String, Object> result = new HashMap<>();
                                JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
                                api.getCallbackBuilder().build().reply(response.toJSONString());
                            } else {
                                System.out.printf("%s Pin transaction was not successful.\n\n\n", PREFIX);
                                sendErrorReply(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), api);
                            }
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        System.out.printf("%s Pin transaction request failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), api.getId());
                        api.getCallbackBuilder().build().reply(response.toJSONString());
                    }
                });
    }
}
