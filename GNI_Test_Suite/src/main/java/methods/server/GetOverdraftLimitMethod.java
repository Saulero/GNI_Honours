package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;

import java.util.HashMap;
import java.util.Map;

public class GetOverdraftLimitMethod {

    public static String parseRequest(JSONRPC2Request reqIn) {


        DummyServerDB db = DummyServerDB.getInstance();
        CustomerAccount customer = null;
        for (CustomerAccount account : db.getCustomers()) {
            if (account.getAuthToken().equals((String) reqIn.getNamedParams().get("authToken"))){
                customer = account;
            }
        }

        if(customer==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        BankAccount bankAccount = null;
        for(BankAccount account : customer.getBankAccounts()){
            if(account.getiBAN().equals((String) reqIn.getNamedParams().get("iBAN"))){
                bankAccount = account;
            }
        }

        if(bankAccount==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        // Construct response message.
        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("overdraftLimit", bankAccount.getLimit());

        JSONRPC2Response response = new JSONRPC2Response(params, reqIn.getID());

        return response.toString();

    }
}