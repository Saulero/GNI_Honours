package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ProvideAccessMethod {

    public static JSONRPC2Request createRequest(CustomerAccount customerAccount, BankAccount bankAccount, CustomerAccount receivingCustomer){
        // The remote method to call
        String method = "provideAccess";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("authToken", customerAccount.getAuthToken());
        params.put("iBAN", bankAccount.getiBAN());
        params.put("username", receivingCustomer.getUsername());

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static PinCard parseResponse(Map<String, Object> namedResults,  BankAccount bankAccount, CustomerAccount receivingCustomer){

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            cal.setTime(sdf.parse(namedResults.get("expirationDate").toString()));// all done
        } catch (ParseException e) {
            e.printStackTrace();
        }

        PinCard pinCard = new PinCard(bankAccount,
                ( namedResults.get("pinCard").toString()),
                ( namedResults.get("pinCode").toString()),
                cal

        );
        receivingCustomer.addPinCard(pinCard);

        return pinCard;
    }
}
