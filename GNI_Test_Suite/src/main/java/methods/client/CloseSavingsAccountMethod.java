package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import models.BankAccount;
import models.CustomerAccount;

import java.util.HashMap;
import java.util.Map;

public class CloseSavingsAccountMethod {

    public static JSONRPC2Request createRequest(CustomerAccount customerAccount, BankAccount bankAccount){
        // The remote method to call
        String method = "closeSavingsAccount";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("authToken", customerAccount.getAuthToken());
        params.put("iBAN", bankAccount.getiBAN());

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static void parseResponse(Map<String, Object> namedResults){
    }
}
