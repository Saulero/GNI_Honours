package api.methods;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import io.advantageous.qbit.reactive.CallbackBuilder;
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

    public static void sendErrorReply(CallbackBuilder callbackBuilder, MessageWrapper reply, final Object id) {
        JSONRPC2Response response;
        if (reply.getData() == null) {
            response = new JSONRPC2Response(new JSONRPC2Error(reply.getCode(), reply.getMessage()), id);
        } else {
            response = new JSONRPC2Response(new JSONRPC2Error(reply.getCode(), reply.getMessage(), reply.getData()), id);
        }
        callbackBuilder.build().reply(response.toJSONString());
    }
}
