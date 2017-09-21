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

import static api.ApiService.ATMNUMBER;
import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public abstract class DepositIntoAccount {

    /**
     * Makes a deposit into an account using a pincard.
     * @param params Parameters of the request (iBAN, pinCard, pinCode, amount).
     * @param api DataBean containing everything in the ApiService
     */
    public static void depositIntoAccount(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Sending deposit transaction.\n", PREFIX);
        String accountNumber = (String) params.get("iBAN");
        String pinCode = (String) params.get("pinCode");
        String pinCard = (String) params.get("pinCard");
        Double amount = (Double) params.get("amount");
        PinTransaction pin = JSONParser.createJsonPinTransaction(ATMNUMBER, accountNumber,
                "", pinCode, Long.parseLong(pinCard), amount, true);
        api.getPinClient().putFormAsyncWith1Param("/services/pin/transaction",
                "request", api.getJsonConverter().toJson(pin), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Transaction reply = (Transaction) messageWrapper.getData();
                            if (reply.isSuccessful() && reply.isProcessed()) {
                                System.out.printf("%s ATM transaction successful.\n\n\n", PREFIX);
                                Map<String, Object> result = new HashMap<>();
                                JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
                                api.getCallbackBuilder().build().reply(response.toJSONString());
                            } else {
                                System.out.printf("%s ATM transaction was not successful.\n\n\n", PREFIX);
                                sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                                        "Unknown error occurred."), api);
                            }
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        System.out.printf("%s ATM transaction request failed, body: %s\n\n\n\n", PREFIX, body);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests"), api.getId());
                        api.getCallbackBuilder().build().reply(response.toJSONString());
                    }
                });
    }
}
