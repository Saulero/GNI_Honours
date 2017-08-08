package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.AccountLink;
import databeans.MessageWrapper;

import static api.ApiService.accountNumberLength;
import static api.ApiService.characterLimit;

/**
 * @author Saul
 */
public class SharedUtilityMethods {

    /**
     * Checks if a the value of a field is larger than 0 and smaller than a preset character limit.
     * @param fieldValue Field to check the value length of.
     * @return Boolean indicating if the length of the string is larger than 0 and smaller than characterLimit.
     */
    public static boolean valueHasCorrectLength(final String fieldValue) {
        int valueLength = fieldValue.length();
        return valueLength > 0 && valueLength < characterLimit;
    }

    //------------------------------------------------------------------------------------------------------------------

    public static void sendErrorReply(MessageWrapper reply, final ApiBean api) {
        JSONRPC2Response response;
        if (reply.getData() == null) {
            response = new JSONRPC2Response(new JSONRPC2Error(
                    reply.getCode(), reply.getMessage()), api.getId());
        } else {
            response = new JSONRPC2Response(new JSONRPC2Error(
                    reply.getCode(), reply.getMessage(), reply.getData()), api.getId());
        }
        api.getCallbackBuilder().build().reply(response.toJSONString());
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
        if (accountNumber == null || accountNumber.length() != accountNumberLength) {
            throw new IncorrectInputException("The following variable was incorrectly specified: accountNumber.");
        }
    }
}
