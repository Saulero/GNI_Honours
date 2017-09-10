package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.MetaMethodData;
import databeans.MethodType;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class SimulateTime {

    /**
     * Process passing of time withint the system.
     * @param params Parameters of the request(nrOfDays, AuthToken).
     * @param api DataBean containing everything in the ApiService
     */
    public static void simulateTime(final Map<String, Object> params, final ApiBean api) {
        long nrOfDays = (Long) params.get("nrOfDays");
        System.out.printf("%s Sending simulate time request.\n", PREFIX);
        MessageWrapper request = JSONParser.createMessageWrapper(
                false, 0, "Admin Request", new MetaMethodData(nrOfDays));
        request.setMethodType(MethodType.SIMULATE_TIME);
        request.setCookie((String) params.get("authToken"));
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/systemInformation",
                "data", api.getJsonConverter().toJson(request), (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                        JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    System.out.printf("%s %s days have now passed on the system.\n\n\n\n",
                            PREFIX, "" + nrOfDays);
                    Map<String, Object> result = new HashMap<>();
                    JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
                    api.getCallbackBuilder().build().reply(response.toJSONString());
                } else {
                    sendErrorReply(messageWrapper, api);
                }
            } else {
                System.out.printf("%s Simulate time request unsuccessful.\n\n\n\n", PREFIX);
                JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500,
                        "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests"), api.getId());
                api.getCallbackBuilder().build().reply(response.toJSONString());
            }
        });
    }
}
