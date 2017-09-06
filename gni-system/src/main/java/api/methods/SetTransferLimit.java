package api.methods;

import api.ApiBean;
import api.ApiService;
import api.IncorrectInputException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.MAX_ACCOUNT_NUMBER_LENGTH;
import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 */
public class SetTransferLimit {
    public static void setTransferLimit(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s sending set transfer limit request.\n", ApiService.PREFIX);
        handleTransferLimitExceptions(params, api);
    }

    private static void handleTransferLimitExceptions(final Map<String, Object> params, final ApiBean api) {
        try {
            verifySetTransferLimitInput(params);
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        }
    }

    private static void verifySetTransferLimitInput(final Map<String, Object> params) throws IncorrectInputException {
        String cookie = (String) params.get("authToken");
        if (cookie == null) {
            throw new IncorrectInputException("authToken not specified.");
        }
        String iBAN = (String) params.get("iBAN");
        if (iBAN == null || iBAN.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("iBAN incorrectly specified.");
        }
        Double transferLimit = (Double) params.get("transferLimit");
        if (transferLimit == null) {
            throw new IncorrectInputException("transferLimit not specified.");
        }
    }

    private static void doSetTransferLimitRequest(final Map<String, Object> params, final ApiBean api) {
        api.getAuthenticationClient().putFormAsyncWith3Params("/services/authentication/transferLimit",
                "cookie", params.get("authToken"), "iBAN", params.get("iBAN"),
                "transferLimit", params.get("transferLimit"),
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendSetTransferLimitCallback(api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests"), api);
                    }
                });
    }

    private static void sendSetTransferLimitCallback(final ApiBean api) {
        System.out.printf("%s Successfully set transfer limit.\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
    }
}
