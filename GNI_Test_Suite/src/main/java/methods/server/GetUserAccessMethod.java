package methods.server;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import models.BankAccount;
import models.CustomerAccount;

import java.util.*;

public class GetUserAccessMethod {


    public static String parseRequest(JSONRPC2Request reqIn){
        boolean error = false;

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

        List<BankAccount> bankAccountList = new LinkedList<>();

        // Add customers bank accounts.
        bankAccountList.addAll(customer.getBankAccounts());



        BankAccount bankAccount = null;
        // Look through all possible bankAccounts.
        for (CustomerAccount customers : db.getCustomers()) {
            for (BankAccount account : customers.getBankAccounts()) {
                if (account.getAccessReceivers().contains(customer)) {
                    bankAccountList.add(account);
                }
            }
        }

//        if(bankAccountList==null){
//            return new JSONRPC2Response(JSONRPC2Error.INVALID_REQUEST, reqIn.getID()).toString();
//        }

        // ToDo. Check if customer has access to bank account.

//        // Construct response message.
//        // The required named parameters to pass
//        Map<String, Object> params = new HashMap<String, Object>();
//        params.put("balance", bankAccount.getBalance());

        Set<Map<String, Object>> paramArray = new HashSet<>();
//        int i = 0;

//        Map<String, Object> params = new HashMap<String, Object>();
//        params.put("username", customer.getUsername());
//        paramArray.add(params);

        // Construct response message.
        // The required named parameters to pass
        for(BankAccount account : bankAccountList){

            Map<String, Object> params2 = new HashMap<String, Object>();
            for (CustomerAccount customerA : db.getCustomers()) {
                if(customerA.getBankAccounts().contains(account)){
                    params2.put("owner", customerA.getUsername());
                    params2.put("iBAN", account.getiBAN());
                }
            }


            paramArray.add(params2);
        }

        JSONRPC2Response response = new JSONRPC2Response(paramArray, reqIn.getID());

        return response.toString();
    }

}
