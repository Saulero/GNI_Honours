package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import util.JSONParser;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static io.advantageous.boon.core.Typ.date;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @Author noel
 */
public class GetEventLogs {
    /**
     * Requests the current system date.
     * @param api DataBean containing everything in the ApiService
     */
    public static void getEventLogs(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Sending event log request.\n", PREFIX);
        api.getSystemInformationClient().getAsyncWith2Params("/services/systemInfo/log",
                "beginDate", params.get("beginDate"), "endDate", params.get("endDate"),
                (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                                        JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    List<Map<String, Object>> logs = (List<Map<String, Object>>) messageWrapper.getData();
                    for(Map<String, Object> log : logs) {
                        System.out.println(log.toString());
                    }
                    System.out.printf("%s retrieved eventlog, forwarding.\n\n\n\n", PREFIX);
                    JSONRPC2Response response = new JSONRPC2Response(logs, api.getId());
                    api.getCallbackBuilder().build().reply(response.toJSONString());
                } else {
                    System.out.printf("%s Date request unsuccessful.\n\n\n\n", PREFIX);
                    sendErrorReply(messageWrapper, api);
                }
            } else {
                System.out.printf("%s Date request unblocking failed, body: %s\n\n\n\n", PREFIX, body);
                JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500,
                        "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests"), api.getId());
                api.getCallbackBuilder().build().reply(response.toJSONString());
            }
        });
    }
}
