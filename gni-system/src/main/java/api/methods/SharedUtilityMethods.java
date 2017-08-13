package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;
import util.JSONParser;

import static api.ApiService.ACCOUNT_NUMBER_LENGTH;
import static api.ApiService.CHARACTER_LIMIT;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class SharedUtilityMethods {

    /**
     * Checks if a the value of a field is larger than 0 and smaller than a preset character limit.
     * @param fieldValue Field to check the value length of.
     * @return Boolean indicating if the length of the string is larger than 0 and smaller than CHARACTER_LIMIT.
     */
    public static boolean valueHasCorrectLength(final String fieldValue) {
        int valueLength = fieldValue.length();
        return valueLength > 0 && valueLength < CHARACTER_LIMIT;
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * Composes an error reply based on a filled MassageWrapper.
     * @param reply The MessageWrapper to extract the data from
     * @param api DataBean containing everything in the ApiService
     */
    public static void sendErrorReply(final MessageWrapper reply, final ApiBean api) {
        JSONRPC2Response response;
        if (reply.getData() == null) {
            response = new JSONRPC2Response(new JSONRPC2Error(
                    reply.getCode(), reply.getMessage()), api.getId());
        } else {
            response = new JSONRPC2Response(new JSONRPC2Error(
                    reply.getCode(), reply.getMessage(), reply.getData()), api.getId());
        }
        api.getSystemInformationClient().putFormAsyncWith1Param("/services/systemInfo/log/error",
                "response", api.getJsonConverter().toJson(response),
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        api.getCallbackBuilder().build().reply(response.toJSONString());
                    } else {
                        api.getCallbackBuilder().build().reply(api.getJsonConverter()
                                                                .toJson(JSONParser.createMessageWrapper(true,
                                                                500, "An unknown error occurred.",
                                                            "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * Checks if the input for an account link request is correctly formatted and contains correct values.
     * @param accountLink {@link AccountLink} that should be created in the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     */
    public static void verifyAccountLinkInput(final AccountLink accountLink)
            throws IncorrectInputException, JsonSyntaxException {
        final String accountNumber = accountLink.getAccountNumber();
        if (accountNumber == null || accountNumber.length() != ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }
}
