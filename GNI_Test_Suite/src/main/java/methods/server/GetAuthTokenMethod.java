package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.security.SecureRandom;
import java.util.*;

public class GetAuthTokenMethod {


    public static String parseRequest(JSONRPC2Request reqIn){
        boolean error = false;

        DummyServerDB db = DummyServerDB.getInstance();

        CustomerAccount customer = null;
        for(CustomerAccount account : db.getCustomers()){
            if(account.getUsername().equals((String) reqIn.getNamedParams().get("username"))){
                customer = account;
            }
        }

        String authToken = "";
        if(customer!=null) {
            if (customer.getPassword().equals((String) reqIn.getNamedParams().get("password"))){
                if(customer.getAuthToken()== null || customer.getAuthToken() == ""){
                    authToken = UUID.randomUUID().toString().replace("-","");
                    customer.setAuthToken(authToken);
                } else {
                    authToken = customer.getAuthToken();
                }
            } else {
                error = true;
            }
        } else {
            error = true;
        }

        System.out.println(authToken);

        JSONRPC2Response response;

        if(error){
            response = new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID());
        } else {

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("authToken", authToken);

            response = new JSONRPC2Response(params, reqIn.getID());
        }


        return response.toString();
    }

}
