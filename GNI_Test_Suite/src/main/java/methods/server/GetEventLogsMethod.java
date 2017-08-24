package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GetEventLogsMethod {


    public static String parseRequest(JSONRPC2Request reqIn){
        boolean error = false;

        DummyServerDB db = DummyServerDB.getInstance();
//
//        int nrOfDays = ((Long) reqIn.getNamedParams().get("nrOfDays")).intValue();
//
//
//        for(int i = 0; i< nrOfDays; i++){
//            db.addDay();
//        }


        Map<String, Object> params = new HashMap<String, Object>();
        Set<Map<String, Object>> paramArray = new HashSet<>();

        JSONRPC2Response response= new JSONRPC2Response(paramArray, reqIn.getID());



        return response.toString();
    }
}
