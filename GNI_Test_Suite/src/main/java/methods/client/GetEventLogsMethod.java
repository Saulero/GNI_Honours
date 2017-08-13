package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author noel
 */
public class GetEventLogsMethod {

    public static JSONRPC2Request createRequest(LocalDate beginDate, LocalDate endDate){
        // The remote method to call
        String method = "getEventLogs";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("beginDate", beginDate.toString());
        params.put("endDate", endDate.toString());

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
