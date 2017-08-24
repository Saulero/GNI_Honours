package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OpenAdditionalAccountMethod {

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

        Random generator = new Random();

        String randomIBAN = "NL69INGB055832" + generator.nextInt(9999);

        BankAccount bankAccount = new BankAccount(randomIBAN);
        customer.addBankAccount(bankAccount);


        String cardNumber = generator.nextInt(9999) +"";
        String pinCode = generator.nextInt(9999) + "";

        PinCard pinCard = new PinCard(bankAccount, cardNumber, pinCode, db.getExpirationCalendar());
        customer.addPinCard(pinCard);


        // Construct response message.
        // The required named parameters to pass
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("iBAN", bankAccount.getiBAN());
        params.put("pinCard", cardNumber);
        params.put("pinCode", pinCode);
        params.put("expirationDate", pinCard.getExpirationDateString());

        JSONRPC2Response response = new JSONRPC2Response(params, reqIn.getID());

        return response.toString();

    }
}