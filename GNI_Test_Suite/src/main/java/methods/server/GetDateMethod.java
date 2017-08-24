package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.CustomerAccount;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GetDateMethod {


    public static String parseRequest(JSONRPC2Request reqIn){
        boolean error = false;

        DummyServerDB db = DummyServerDB.getInstance();

        JSONRPC2Response response;

        if(error){
            response = new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID());
        } else {

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("date", CalanderUTIL.CalanderToDateString(db.getCalendar()));

            response = new JSONRPC2Response(params, reqIn.getID());
        }

        return response.toString();
    }

}
