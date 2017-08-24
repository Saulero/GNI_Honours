package methods.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import models.AccountCardTuple;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class OpenAccountMethod {

    public static JSONRPC2Request createRequest(CustomerAccount customerAccount){
        // The remote method to call
        String method = "openAccount";

        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("name", customerAccount.getName());
        params.put("surname", customerAccount.getSurname());
        params.put("initials", customerAccount.getInitials());
        params.put("dob", customerAccount.getDob());
        params.put("ssn", customerAccount.getSsn());
        params.put("address", customerAccount.getAddress());
        params.put("telephoneNumber", customerAccount.getTelephoneNumber());
        params.put("email", customerAccount.getEmail());
        params.put("username", customerAccount.getUsername());
        params.put("password", customerAccount.getPassword());

        // The mandatory request ID
        String id = "req-001";

        // Create a new JSON-RPC 2.0 request
        JSONRPC2Request reqOut = new JSONRPC2Request(method, params, id);

        // Serialise the request to a JSON-encoded string
        String jsonString = reqOut.toString();

        return reqOut;
    }

    public static AccountCardTuple parseResponse(Map<String, Object> namedResults, CustomerAccount customerAccount){

        // Assume everything went right.
        BankAccount bankAccount = new BankAccount((String) namedResults.get("iBAN"));
        customerAccount.addBankAccount(bankAccount);


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
        customerAccount.addPinCard(pinCard);

        return new AccountCardTuple(bankAccount,pinCard);
    }
}
