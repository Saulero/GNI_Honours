package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ProvideAccessMethod {

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

        CustomerAccount customerReceivingAccess = null;
        for (CustomerAccount account : db.getCustomers()) {
            if (account.getUsername().equals((String) reqIn.getNamedParams().get("username"))){
                customerReceivingAccess = account;
            }
        }

        if(customerReceivingAccess==null){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        // Add access to bank account.
        bankAccount.addAccessReceiver(customerReceivingAccess);

        //Create card for access.
        Random generator = new Random();
        String cardNumber = generator.nextInt(9999) + "";
        String pinCode = generator.nextInt(9999) + "";

        PinCard pinCard = new PinCard(bankAccount, cardNumber, pinCode);
        customerReceivingAccess.addPinCard(pinCard);


        // Construct response message.
        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("iBAN", bankAccount.getiBAN());
        params.put("pinCard", cardNumber);
        params.put("pinCode", pinCode);

        JSONRPC2Response response = new JSONRPC2Response(params, reqIn.getID());

        return response.toString();

    }
}