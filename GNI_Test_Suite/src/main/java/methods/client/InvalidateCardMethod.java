package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Saul
 */
public class InvalidateCardMethod {

    public static JSONRPC2Request createRequest(CustomerAccount customerAccount, BankAccount bankAccount, PinCard pinCard, boolean newPin){
        // The remote method to call
        String method = "invalidateCard";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("authToken", customerAccount.getAuthToken());
        params.put("iBAN", bankAccount.getiBAN());
        params.put("pinCard", pinCard.getPinCardNumber());
        params.put("newPin", Boolean.toString(newPin));

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static PinCard parseResponse(Map<String, Object> namedResults ){
        // Assume everything went right.
        return new PinCard(null, (String) namedResults.get("pinCard"), (String) namedResults.get("pinCode"));
    }
}
