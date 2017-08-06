package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;

public class TransferMoneyMethod {

    public static String parseRequest(JSONRPC2Request reqIn) {


        DummyServerDB db = DummyServerDB.getInstance();

        BankAccount bankAccount = null;
        // Look through all possible bankAccounts.
        for (CustomerAccount customers : db.getCustomers()) {
            for (BankAccount account : customers.getBankAccounts()) {
                if (account.getiBAN().equals((String) reqIn.getNamedParams().get("sourceIBAN"))) {
                    bankAccount = account;
                }
            }
        }

        if(bankAccount==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        BankAccount bankAccount2 = null;
        // Look through all possible bankAccounts.
        for (CustomerAccount customers : db.getCustomers()) {
            for (BankAccount account : customers.getBankAccounts()) {
                if (account.getiBAN().equals((String) reqIn.getNamedParams().get("targetIBAN"))) {
                    bankAccount2 = account;
                }
            }
        }

        if(bankAccount2==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }


        CustomerAccount customer = null;
        for (CustomerAccount account : db.getCustomers()) {
            if (account.getAuthToken().equals((String) reqIn.getNamedParams().get("authToken"))){
                customer = account;
            }
        }

        // ToDo. Check if customer has access to bankAccount.

        // TODo. Do both accounts have to exist? Maybe One is sufficient?

        bankAccount.processTransfer(
                (String) reqIn.getNamedParams().get("sourceIBAN"),
                (String) reqIn.getNamedParams().get("targetIBAN"),
                (String) reqIn.getNamedParams().get("targetName"),
                ((Double) reqIn.getNamedParams().get("amount")).floatValue(),
                (String) reqIn.getNamedParams().get("description")
        );
        bankAccount2.processTransfer(
                (String) reqIn.getNamedParams().get("sourceIBAN"),
                (String) reqIn.getNamedParams().get("targetIBAN"),
                (String) reqIn.getNamedParams().get("targetName"),
                ((Double) reqIn.getNamedParams().get("amount")).floatValue(),
                (String) reqIn.getNamedParams().get("description")
        );
//        bankAccount.lowerBalance(((Double) reqIn.getNamedParams().get("amount")).floatValue());
//        bankAccount.increaseBalance(((Double) reqIn.getNamedParams().get("amount")).floatValue());

        // Construct response message.
        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
//        params.put("result", true);

        JSONRPC2Response response = new JSONRPC2Response(params, reqIn.getID());

        return response.toString();

    }
}