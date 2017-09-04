package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import models.BankAccount;
import models.CustomerAccount;

import java.util.HashMap;
import java.util.Map;

public class SetFreezeUserAccountMethod {

    public static JSONRPC2Request createRequest(CustomerAccount admin, boolean freeze, CustomerAccount target){
        // The remote method to call
        String method = "setFreezeUserAccount";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("authToken", admin.getAuthToken());
        params.put("username", target.getUsername());
        params.put("freeze", freeze);

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static void parseResponse(Map<String, Object> namedResults){

        // Assume everything went OK. otherwise error.
    }
}
