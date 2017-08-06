package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class RevokeAccessMethod {

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
        // Look through all possible bankAccounts.
        for (CustomerAccount customers : db.getCustomers()) {
            for (BankAccount account : customers.getBankAccounts()) {
                if (account.getiBAN().equals((String) reqIn.getNamedParams().get("iBAN"))) {
                    bankAccount = account;
                }
            }
        }

        if(bankAccount==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        CustomerAccount customerLosingAccess = null;
        for (CustomerAccount account : db.getCustomers()) {
            if (account.getUsername().equals((String) reqIn.getNamedParams().get("username"))){
                customerLosingAccess = account;
            }
        }

        if(customerLosingAccess==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        // Remove PIN CARD
        for (Iterator<PinCard> i = customerLosingAccess.getPinCards().iterator(); i.hasNext();) {
            PinCard element = i.next();
            if (element.getBankAccount().getiBAN().equals(bankAccount.getiBAN())) {
                i.remove();
            }
        }

        // remove access from BankAccount
        bankAccount.getAccessReceivers().remove(customerLosingAccess);

        // Construct response message.
        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
//        params.put("result", true);

        JSONRPC2Response response = new JSONRPC2Response(params, reqIn.getID());

        return response.toString();

    }
}