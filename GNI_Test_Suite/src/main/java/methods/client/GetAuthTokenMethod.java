package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import methods.server.DummyServerDB;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GetAuthTokenMethod {

    public static JSONRPC2Request createRequest(CustomerAccount customerAccount){
        // The remote method to call
        String method = "getAuthToken";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("username", customerAccount.getUsername());
        params.put("password", customerAccount.getPassword());

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static void parseResponse(Map<String, Object> namedResults, CustomerAccount customerAccount){
        customerAccount.setAuthToken((String) namedResults.get("authToken"));
    }

}
