package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;

public class TransferMoneyMethod {

    public static JSONRPC2Request createRequest(BankAccount bankAccount, BankAccount targetBankAccount,
                                                CustomerAccount customerAccount, double amount,
                                                String description){
        // The remote method to call
        String method = "transferMoney";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("sourceIBAN", bankAccount.getiBAN());
        params.put("targetIBAN", targetBankAccount.getiBAN());
        params.put("authToken", customerAccount.getAuthToken());
        params.put("targetName", customerAccount.getName());
        params.put("description", description);
        params.put("amount", amount);

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
