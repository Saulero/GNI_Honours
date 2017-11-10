package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;
import databeans.MethodType;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static api.methods.SharedUtilityMethods.verifyAccountLinkInput;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public abstract class TransferBankAccount {

    public static void transferBankAccount(final Map<String, Object> params, final ApiBean api) {
        String accountNumber = (String) params.get("iBAN");
        String username = (String) params.get("username");
        String cookie = (String) params.get("authToken");
        AccountLink accountLink = JSONParser.createJsonAccountLink(accountNumber, username, false);
        System.out.printf("%s Sending TransferBankAccount request.\n", PREFIX);
        handleTransferBankAccountExceptions(accountLink, cookie, api);
    }

    private static void handleTransferBankAccountExceptions(
            final AccountLink accountLink, final String cookie, final ApiBean api) {
        try {
            verifyAccountLinkInput(accountLink.getAccountNumber());
            doTransferBankAccountRequest(accountLink, cookie, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", e.getMessage()), api);
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), api);
        }
    }

    private static void doTransferBankAccountRequest(
            final AccountLink accountLink, final String cookie, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.TRANSFER_BANK_ACCOUNT);
        data.setData(accountLink);

        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/transferBankAccount",
                "data", api.getJsonConverter().toJson(data),
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(accountLinkReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendTransferBankAccountCallback(api);
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

    private static void sendTransferBankAccountCallback(final ApiBean api) {
        System.out.printf("%s Bank Account Transfer successful.\n\n\n\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
