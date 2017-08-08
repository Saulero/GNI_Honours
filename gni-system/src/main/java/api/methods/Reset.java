package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class Reset {

    /**
     * Resets the system's Database and system time.
     */
    public static void reset(final ApiBean api) {
        System.out.printf("%s Sending Reset request.\n", PREFIX);
        api.getSystemInformationClient().postAsync("/services/systemInfo/reset", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    LocalDate date = (LocalDate) messageWrapper.getData();
                    System.out.printf("%s Reset successful, the current date is: %s\n\n\n\n", PREFIX, date.toString());
                    Map<String, Object> result = new HashMap<>();
                    JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
                    api.getCallbackBuilder().build().reply(response.toJSONString());
                } else {
                    System.out.printf("%s Reset unsuccessful.\n\n\n\n", PREFIX);
                    sendErrorReply(messageWrapper, api);
                }
            } else {
                System.out.printf("%s Reset request failed, body: %s\n\n\n\n", PREFIX, body);
                JSONRPC2Response response = new JSONRPC2Response(new JSONRPC2Error(500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), api.getId());
                api.getCallbackBuilder().build().reply(response.toJSONString());
            }
        });
    }
}
