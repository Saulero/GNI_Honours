package api.methods;

import api.ApiBean;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.FreezeAccount;
import databeans.MessageWrapper;
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
public class SetFreezeUserAccount {

    public static void setFreezeUserAccount(final Map<String, Object> params, final ApiBean api) {
        String cookie = (String) params.get("authToken");
        String username = (String) params.get("username");
        boolean freeze = (boolean) params.get("freeze");
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.SET_FREEZE_USER_ACCOUNT);
        data.setData(new FreezeAccount(freeze, username));
        System.out.printf("%s Sending setFreezeUserAccount request.\n", PREFIX);
        doSetFreezeUserAccountRequest(data, api);
    }

    private static void doSetFreezeUserAccountRequest(final MessageWrapper data, final ApiBean api) {
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/setFreezeUserAccount",
                "data", api.getJsonConverter().toJson(data),
                ((httpStatusCode, httpContentType, reply) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(reply), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendSetFreezeUserAccountCallback(api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests"), api);
                    }
                }));
    }

    private static void sendSetFreezeUserAccountCallback(final ApiBean api) {
        System.out.printf("%s SetFreezeUserAccount request successful.\n\n\n\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
