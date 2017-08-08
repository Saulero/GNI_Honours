package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Saul
 */
public class ResetMethod {

    public static JSONRPC2Request createRequest(){
        // The remote method to call
        String method = "reset";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static void parseResponse(Map<String, Object> namedResults ){

        // Assume success
    }
}
