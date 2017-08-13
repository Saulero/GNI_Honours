import client.DummyClient;
import client.IClient;
import client.SocketClient;
import client.TestHttpClient;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import methods.client.*;
import methods.client.CloseAccountMethod;
import methods.client.DepositIntoAccountMethod;
import methods.client.GetAuthTokenMethod;
import methods.client.GetBalanceMethod;
import methods.client.GetBankAccountAccessMethod;
import methods.client.GetTransactionsMethod;
import methods.client.GetUserAccessMethod;
import methods.client.OpenAccountMethod;
import methods.client.OpenAdditionalAccountMethod;
import methods.client.PayFromAccountMethod;
import methods.client.ProvideAccessMethod;
import methods.client.RevokeAccessMethod;
import methods.client.TransferMoneyMethod;
import methods.server.*;
import models.AccountCardTuple;
import models.BankAccount;
import models.CustomerAccount;
import models.PinCard;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BasicHappyFlowTestSuite {

        public static void main(String[] args) {

            // Setup the Client here.
            IClient client = new TestHttpClient();

            // Create CustomerAccount.
            CustomerAccount customer1 = new CustomerAccount("Duck", "Donald", "D", new Date().toString(),
                    "571376046", "1313 Webfoot Walk, Duckburg", "+316 12345678", "donald@gmail.com",
                    "duckd", "kwikkwekkwak");
            CustomerAccount customer2 = new CustomerAccount("Duck", "Daisy", "D", new Date().toString(),
                    "571376047", "1313 Webfoot Walk, Duckburg", "+316 12345679", "daisy@gmail.com",
                    "daisyduck", "donald");

            BankAccount bankAccount1 = null;
            BankAccount bankAccount2 = null;
            BankAccount bankAccount3 = null;

            PinCard card1 = null;
            PinCard card2 = null;
            PinCard card3 = null;
            PinCard card4 = null;

            AccountCardTuple tuple = null;

            JSONRPC2Request request;
            JSONRPC2Response response;
            Map<String, Object> parsedResponse;

            // SimulateTime, Reset and getDate method
            System.out.println("-- SimulateTime & Reset & GetDate method --");

            // getDate
            System.out.println("--> getDate");
            request = GetDateMethod.createRequest();
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetDateMethod.parseResponse(parsedResponse);
            }

            // simulateTime
            System.out.println("--> simulateTime with 1000 days");
            request = SimulateTimeMethod.createRequest(1000L);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                SimulateTimeMethod.parseResponse(parsedResponse);
            }

            // getDate
            System.out.println("--> getDate");
            request = GetDateMethod.createRequest();
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetDateMethod.parseResponse(parsedResponse);
            }

            // reset
            System.out.println("--> reset");
            request = ResetMethod.createRequest();
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                ResetMethod.parseResponse(parsedResponse);
            }

            // getDate
            System.out.println("--> getDate");
            request = GetDateMethod.createRequest();
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetDateMethod.parseResponse(parsedResponse);
            }


            // Method 1. OpenAccount.
            System.out.println("-- OpenAccountMethod. Donald opens an account --");

            request = OpenAccountMethod.createRequest(customer1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                tuple = OpenAccountMethod.parseResponse(parsedResponse, customer1);
                bankAccount1 = tuple.getAccount();
                card1 = tuple.getCard();
            }

            // Method 2. getAccountAuth.
            System.out.println("-- getAccountAuth Method. Donald logs in. --");

            request = GetAuthTokenMethod.createRequest(customer1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetAuthTokenMethod.parseResponse(parsedResponse, customer1);
            }

            // Method 2. OpenAdditonalAccount.
            System.out.println("-- OpenAdditonalAccount Method. Donald wants a holiday savings account --");

            request = OpenAdditionalAccountMethod.createRequest(customer1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                tuple = OpenAdditionalAccountMethod.parseResponse(parsedResponse, customer1);
                bankAccount2 = tuple.getAccount();
                card2 = tuple.getCard();
            }


            // Access Module
            System.out.println("--Open Account Method for Daisy--");

            request = OpenAccountMethod.createRequest(customer2);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                tuple = OpenAccountMethod.parseResponse(parsedResponse, customer2);
                bankAccount3 = tuple.getAccount();
                card3 = tuple.getCard();
            }

            System.out.println("-- Daisy logs in. --");

            request = GetAuthTokenMethod.createRequest(customer2);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetAuthTokenMethod.parseResponse(parsedResponse, customer2);
            }

            // ProvideAccessMethod
            System.out.println("-- ProvideAccessMethod. Donald shares access with Daisy--");

            request = ProvideAccessMethod.createRequest(customer1, bankAccount1, customer2);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                card4 = ProvideAccessMethod.parseResponse(parsedResponse, bankAccount1, customer2);
            }

            // DepositIntoAccount
            System.out.println("-- DepositIntoAccount. Donald deposits his salary --");

            request = DepositIntoAccountMethod.createRequest(bankAccount1, card1, 313);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                DepositIntoAccountMethod.parseResponse(parsedResponse);
            }

            // PayFromAccount
            System.out.println("-- PayFromAccount. Donald buys hot dogs --");

            request = PayFromAccountMethod.createRequest(bankAccount1, bankAccount3, card1, (12.3));
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                PayFromAccountMethod.parseResponse(parsedResponse);
            }

            // UnblockCard
            System.out.println("-- UnblockCard. Donald uses the wrong pin code and then unblocks his card --");

            String pinCode = card1.getPinCode();
            card1.setPinCode("999999");

            for (int i = 0; i < 4; i++) {
                System.out.println("--> Uses incorrect PIN");
                request = PayFromAccountMethod.createRequest(bankAccount1, bankAccount3, card1, (1));
                response = client.processRequest(request);

                if ((parsedResponse = checkResponse(response)) != null) {
                    PayFromAccountMethod.parseResponse(parsedResponse);
                }
            }

            System.out.println("--> Unblock Card");
            request = UnblockCardMethod.createRequest(customer1, bankAccount1, card1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                PayFromAccountMethod.parseResponse(parsedResponse);
            }

            card1.setPinCode(pinCode);

            System.out.println("--> Uses correct PIN afterwards");
            request = PayFromAccountMethod.createRequest(bankAccount1, bankAccount3, card1, (1));
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                PayFromAccountMethod.parseResponse(parsedResponse);
            }

            // TransferMoney
            System.out.println("-- TransferMoney. Daisy transfers to Donald --");

            request = TransferMoneyMethod.createRequest(bankAccount3, bankAccount1, customer2, 200, "Moniez");
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                TransferMoneyMethod.parseResponse(parsedResponse);
            }

            // ObtainBalance
            System.out.println("-- Donald wants to obtain his balance --");

            request = GetBalanceMethod.createRequest(customer1, bankAccount1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetBalanceMethod.parseResponse(parsedResponse);
            }

            // getTransactionOverview
            System.out.println("-- Donald wants to get transaction overview --");

            request = GetTransactionsMethod.createRequest(customer1, bankAccount1, 2);
            response = client.processRequest(request);

            List<Map<String, Object>> namedArrayResults = null;
            if ((namedArrayResults = checkArrayResponse(response)) != null) {
                GetTransactionsMethod.parseResponse(namedArrayResults);
            }

            // GetUserAccess
            System.out.println("-- Donald wants to obtain his access. --");

            request = GetUserAccessMethod.createRequest(customer1);
            response = client.processRequest(request);

            if ((namedArrayResults = checkArrayResponse(response)) != null) {
                GetUserAccessMethod.parseResponse(namedArrayResults);
            }


            // GetBankAccountAccessMethod
            System.out.println("-- Donald wants to fetch his current overdraft limit --");

            request = GetOverdraftLimitMethod.createRequest(customer1, bankAccount1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetOverdraftLimitMethod.parseResponse(parsedResponse);
            }

            // SetOverdraftLimitMethod
            System.out.println("-- Donald wants to set his overdraft limit --");

            request = SetOverdraftLimitMethod.createRequest(customer1, bankAccount1, "2500");
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                SetOverdraftLimitMethod.parseResponse(parsedResponse);
            }

            // GetOverdraftLimitMethod
            System.out.println("-- Donald wants to fetch his current overdraft limit --");

            request = GetOverdraftLimitMethod.createRequest(customer1, bankAccount1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetOverdraftLimitMethod.parseResponse(parsedResponse);
            }

            // TransferMoney
            System.out.println("-- TransferMoney. Donald transfers to Daisy and goes overdraft --");

            request = TransferMoneyMethod.createRequest(bankAccount1, bankAccount3, customer1, 1299.7, "Moniez");
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                TransferMoneyMethod.parseResponse(parsedResponse);
            }

            // simulateTime
            System.out.println("-- 365 days pass. --");
            request = SimulateTimeMethod.createRequest(365L);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                SimulateTimeMethod.parseResponse(parsedResponse);
            }

            // getTransactionOverview
            System.out.println("-- Donald wants to get transaction overview --");

            request = GetTransactionsMethod.createRequest(customer1, bankAccount1, 25);
            response = client.processRequest(request);

            if ((namedArrayResults = checkArrayResponse(response)) != null) {
                GetTransactionsMethod.parseResponse(namedArrayResults);
            }

            // ObtainBalance
            System.out.println("-- Donald wants to obtain his balance --");

            request = GetBalanceMethod.createRequest(customer1, bankAccount1);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                GetBalanceMethod.parseResponse(parsedResponse);
            }

            //getEventLog for the past 400 days
            System.out.println("-- Donald wants to get an event overview --");

            request = GetEventLogsMethod.createRequest(LocalDate.now().minusDays(2), LocalDate.now().plusYears(2));
            response = client.processRequest(request);

            if ((namedArrayResults = checkArrayResponse(response)) != null) {
                GetTransactionsMethod.parseResponse(namedArrayResults);
            }


            ///------ TEAR DOWN TESTS.
            // RevokeAccessMethod
            System.out.println("-- RevokeAccessMethod. Donald revokes Daisy's access--");

            request = RevokeAccessMethod.createRequest(customer1, bankAccount1, customer2);
            response = client.processRequest(request);

            if ((parsedResponse = checkResponse(response)) != null) {
                RevokeAccessMethod.parseResponse(parsedResponse, bankAccount1, customer2);
            }

            // Method 3. Close both accounts.
            System.out.println("-- CloseAccount method. Donald closes all his Bank Accounts --");
            for (BankAccount bankAccount : customer1.getBankAccounts()) {

                request = CloseAccountMethod.createRequest(customer1, bankAccount);
                response = client.processRequest(request);

                if ((parsedResponse = checkResponse(response)) != null) {
                    CloseAccountMethod.parseResponse(parsedResponse);
                }
            }
        }

    public static Map<String, Object> checkResponse(JSONRPC2Response respIn){
        Map<String, Object> namedResults = null;

        // Check for success or error
        if (!respIn.indicatesSuccess()) {

            System.out.println("The request failed :");

            JSONRPC2Error err = respIn.getError();

            System.out.println("\terror.code    : " + err.getCode());
            System.out.println("\terror.message : " + err.getMessage());
            System.out.println("\terror.data    : " + err.getData());

        }
        else {
            System.out.println("The request succeeded :");

            System.out.println("\tresult : " + respIn.getResult());
            System.out.println("\tid     : " + respIn.getID());

            namedResults
                    = (Map<String, Object>) respIn.getResult();


        }

        return namedResults;
    }

    public static List<Map<String, Object>> checkArrayResponse(JSONRPC2Response respIn){
        List<Map<String, Object>> namedArrayResults = null;

        // Check for success or error
        if (!respIn.indicatesSuccess()) {

            System.out.println("The request failed :");

            JSONRPC2Error err = respIn.getError();

            System.out.println("\terror.code    : " + err.getCode());
            System.out.println("\terror.message : " + err.getMessage());
            System.out.println("\terror.data    : " + err.getData());

        }
        else {
            System.out.println("The request succeeded :");

            System.out.println("\tresult : " + respIn.getResult());
            System.out.println("\tid     : " + respIn.getID());

             namedArrayResults = (List<Map<String, Object>>) respIn.getResult();


        }

        return namedArrayResults;
    }
}
