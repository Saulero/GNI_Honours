package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;

public class SimulateTimeMethod {


    public static String parseRequest(JSONRPC2Request reqIn){
        boolean error = false;

        DummyServerDB db = DummyServerDB.getInstance();

        int nrOfDays = ((Long) reqIn.getNamedParams().get("nrOfDays")).intValue();


        for(int i = 0; i< nrOfDays; i++){
            db.addDay();
        }


        Map<String, Object> params = new HashMap<String, Object>();
//        params.put("result", true);

        JSONRPC2Response response= new JSONRPC2Response(params, reqIn.getID());



        return response.toString();
    }
}
