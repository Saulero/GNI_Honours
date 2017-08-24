package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;

public class PayFromAccountMethod {

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

        CustomerAccount customer = null;
        PinCard pinCard = null;
        for (CustomerAccount account : db.getCustomers()) {
            for(PinCard card : account.getPinCards()){
                if(card.getBankAccount().getiBAN().equals((String) reqIn.getNamedParams().get("sourceIBAN"))
                        && card.getPinCardNumber().equals((reqIn.getNamedParams().get("pinCard")))){
                    // TODO: Check if this customer has access to account.
                    customer  = account;
                    pinCard = card;
                }
            }
        }

        if(customer==null ){
            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        }

        //TODO: move this code into PinCard
        // Check if pincode is valid
        if(!pinCard.getPinCode().equals((reqIn.getNamedParams().get("pinCode")))){

            if(pinCard.getFailedAttempts() >= 2){
                pinCard.setBlocked(true);
            }
            pinCard.setFailedAttempts(pinCard.getFailedAttempts()+1);

            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
        } else {
            if(pinCard.getBlocked()){
                return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
            }
            pinCard.setFailedAttempts(0);
        }

        // process the payment.
        bankAccount.processCardPayment((String) reqIn.getNamedParams().get("sourceIBAN"),
                (String) reqIn.getNamedParams().get("targetIBAN"),
                ((Double) reqIn.getNamedParams().get("amount")).floatValue()
                );


            // TODO. Check where the money goes?

        // Construct response message.
        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
//        params.put("result", true);

        JSONRPC2Response response = new JSONRPC2Response(params, reqIn.getID());

        return response.toString();

    }
}