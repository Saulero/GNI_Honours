package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.util.*;

public class OpenAccountMethod {


    public static String parseRequest(JSONRPC2Request reqIn){

        DummyServerDB db = DummyServerDB.getInstance();

        CustomerAccount customerAccount = new CustomerAccount((String) reqIn.getNamedParams().get("name"),
                "", "", (String) reqIn.getNamedParams().get("dob"), "", "","", "",
                (String) reqIn.getNamedParams().get("username"),
                (String) reqIn.getNamedParams().get("password"));

        db.addCustomer(customerAccount);

        Random generator = new Random();
        String randomIBAN = "NL69INGB055832" + generator.nextInt(9999);

        BankAccount bankAccount = new BankAccount(randomIBAN);
        customerAccount.addBankAccount(bankAccount);

        String cardNumber = generator.nextInt(9999) +"";
        String pinCode  = generator.nextInt(9999) + "";

        PinCard pinCard = new PinCard(bankAccount, cardNumber, pinCode, db.getExpirationCalendar());
        customerAccount.addPinCard(pinCard);


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
