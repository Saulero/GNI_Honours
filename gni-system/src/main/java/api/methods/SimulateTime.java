package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
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
     * @param params Parameters of the request(nrOfDays).
     */
    public static void simulateTime(final Map<String, Object> params, final ApiBean api) {
        Long nrOfDays = (Long) params.get("nrOfDays");
        System.out.printf("%s Sending simulate time request.\n", PREFIX);
        api.getSystemInformationClient().putFormAsyncWith1Param("/services/systemInfo/date/increment",
                "days", api.getJsonConverter().toJson(nrOfDays), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            System.out.printf("%s %s days have now passed on the system.\n\n\n\n", PREFIX, "" + nrOfDays);
                            Map<String, Object> result = new HashMap<>();
                            JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
                            api.getCallbackBuilder().build().reply(response.toJSONString());
                        } else {
                            System.out.printf("%s Simulate time request unsuccessful.\n\n\n\n", PREFIX);
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        System.out.printf("%s Simulate time request unsuccessful.\n\n\n\n", PREFIX);
                        JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), api.getId());
                        api.getCallbackBuilder().build().reply(response.toJSONString());
                    }
                });
    }
}
