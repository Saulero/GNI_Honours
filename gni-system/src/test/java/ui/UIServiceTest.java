package ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import databeans.*;
import org.junit.Before;
import org.junit.Test;
import sun.security.krb5.internal.AuthorizationData;
import util.JSONParser;

import static org.junit.Assert.*;

/**
 * @author Noel
 */
public class UIServiceTest {

    private UIService ui;
    private Gson jsonConverter;

    @Before
    public void setUp() throws Exception {
        ui = new UIService(0, "");
        jsonConverter = new Gson();
    }

    @Test
    public void verifyDataRequestCorrectInput() {
        DataRequest request = JSONParser.createJsonDataRequest("NL00GNIB0000000000",
                                                                        RequestType.ACCOUNTS, 0L);
        Transaction incorrectObject = JSONParser.createJsonTransaction(-1,
                                                                "NL00GNIB0000000000",
                                                                "NL00GNIB0000000000",
                                                                "De Weerd", "pakketje",
                                                                20.00,false, false);
        String invalidJson = "accountNumber";

        try {
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            request.setType(RequestType.BALANCE);
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            request.setType(RequestType.CUSTOMERDATA);
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            request.setType(RequestType.ACCOUNTEXISTS);
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            request.setType(RequestType.TRANSACTIONHISTORY);
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            request.setAccountNumber(null);
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
            fail("IncorrectInputException not thrown when it should have been thrown.");
        } catch (IncorrectInputException e) {
            request.setAccountNumber("NL00GNIB0000000000");
        }
        try {
            request.setType(null);
            ui.verifyDataRequestInput(jsonConverter.toJson(request));
            fail("IncorrectInputException not thrown when it should have been thrown.");
        } catch (IncorrectInputException e) {
            request.setType(RequestType.TRANSACTIONHISTORY);
        }
        try {
            ui.verifyDataRequestInput(invalidJson);
            fail("JsonSyntax exception not thrown.");
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when a jsonSyntaxException should have been thrown.");
        } catch (JsonSyntaxException e) {
        }
        try {
            ui.verifyDataRequestInput(jsonConverter.toJson(incorrectObject));
            fail("IncorrectInputException not thrown.");
        } catch (IncorrectInputException e) {
        } catch (JsonSyntaxException e) {
            fail("JsonSyntaxException thrown when a IncorrectInputException should have been thrown.");
        }
    }

    @Test
    public void verifyTransactionInput() {
        Transaction transaction = JSONParser.createJsonTransaction(-1,
                                                    "NL00GNIB0000000000",
                                                    "NL00GNIB0000000000",
                                                    "De Weerd", "pakketje",
                                                    20.00,false, false);
        Transaction transaction1 = JSONParser.createJsonTransaction(-1,
                "NL00GNIB0000000000",
                "NL00GNIB0000000000",
                "De Weerd", "pakketje",
                20.00);
        DataRequest incorrectObject = JSONParser.createJsonDataRequest("NL00GNIB0000000000",
                RequestType.ACCOUNTS, 0L);
        String invalidJson = "accountNumber";
        try {
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            ui.verifyTransactionInput(jsonConverter.toJson(transaction1));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it shouldn't be thrown.");
        }
        try {
            transaction.setSourceAccountNumber(null);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setSourceAccountNumber("NL00GNIB0000000000");
        }
        try {
            transaction.setDestinationAccountNumber(null);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setDestinationAccountNumber("NL00GNIB0000000000");
        }
        try {
            transaction.setDestinationAccountHolderName(null);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setDestinationAccountHolderName("De Weerd");
        }
        try {
            transaction.setDescription(null);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setDescription("pakketje");
        }
        try {
            transaction.setTransactionAmount(-20.00);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setTransactionAmount(20.00);
        }
        try {
            transaction.setProcessed(true);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setProcessed(false);
        }
        try {
            transaction.setSuccessful(true);
            ui.verifyTransactionInput(jsonConverter.toJson(transaction));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            transaction.setSuccessful(false);
        }
        try {
            ui.verifyTransactionInput(invalidJson);
            fail("JsonSyntax exception not thrown.");
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when a jsonSyntaxException should have been thrown.");
        } catch (JsonSyntaxException e) {
        }
        try {
            ui.verifyTransactionInput(jsonConverter.toJson(incorrectObject));
            fail("IncorrectInputException not thrown.");
        } catch (IncorrectInputException e) {
        } catch (JsonSyntaxException e) {
            fail("JsonSyntaxException thrown when a IncorrectInputException should have been thrown.");
        }
    }

    @Test
    public void verifyNewCustomerInput() {
        Customer customer = JSONParser.createJsonCustomer("B.D.", "Bert", "De Wilde",
                "bert@bol.com", "0675754756", "bertstraat 5", "16-05-1993",
                989384755L, 1000, 1000, 0L,
                "henk", "henk");
        DataRequest incorrectObject = JSONParser.createJsonDataRequest("NL00GNIB0000000000",
                RequestType.ACCOUNTS, 0L);
        String invalidJson = "accountNumber";
        try {
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it should not have been thrown.");
        }
        try {
            Account account = new Account();
            account.setBalance(0);
            account.setSpendingLimit(0);
            customer.setAccount(account);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it should not have been thrown.");
        }
        try {
            customer.setInitials(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setInitials("B.D.");
        }
        try {
            customer.setName(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setName("Henk");
        }
        try {
            customer.setSurname(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setSurname("De weerd");
        }
        try {
            customer.setEmail(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setEmail("bob@bol.com");
        }
        try {
            customer.setTelephoneNumber(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setTelephoneNumber("0675758473");
        }
        try {
            customer.setAddress(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setAddress("bobstraat 2");
        }
        try {
            customer.setDob(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setDob("16-05-1993");
        }
        try {
            customer.setUsername(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setUsername("henk");
        }
        try {
            customer.setPassword(null);
            ui.verifyNewCustomerInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setPassword("henk");
        }
        try {
            ui.verifyNewCustomerInput(invalidJson);
            fail("JsonSyntax exception not thrown.");
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when a jsonSyntaxException should have been thrown.");
        } catch (JsonSyntaxException e) {
        }
        try {
            ui.verifyNewCustomerInput(jsonConverter.toJson(incorrectObject));
            fail("IncorrectInputException not thrown.");
        } catch (IncorrectInputException e) {
        } catch (JsonSyntaxException e) {
            fail("JsonSyntaxException thrown when a IncorrectInputException should have been thrown.");
        }
    }

    @Test
    public void verifyAccountLinkInput() {
        AccountLink request = JSONParser.createJsonAccountLink("NL00GNIB0000000000", 0L);
        Authentication incorrectObject = JSONParser.createJsonAuthentication("asds", AuthenticationType.LOGIN);
        String invalidJson = "accountNumber";
        try {
            ui.verifyAccountLinkInput(jsonConverter.toJson(request));
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when it should not have been thrown.");
        }
        try {
            request.setAccountNumber(null);
            ui.verifyAccountLinkInput(jsonConverter.toJson(request));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            request.setAccountNumber("NL00GNIB0000000000");
        }
        try {
            ui.verifyAccountLinkInput(invalidJson);
            fail("JsonSyntax exception not thrown.");
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when a jsonSyntaxException should have been thrown.");
        } catch (JsonSyntaxException e) {
        }
        try {
            ui.verifyAccountLinkInput(jsonConverter.toJson(incorrectObject));
            fail("IncorrectInputException not thrown.");
        } catch (IncorrectInputException e) {
        } catch (JsonSyntaxException e) {
            fail("JsonSyntaxException thrown when a IncorrectInputException should have been thrown.");
        }
    }

    @Test
    public void verifyNewAccountInput() {
        Customer customer = JSONParser.createJsonCustomer("B.D.", "Bert", "De Wilde",
                "bert@bol.com", "0675754756", "bertstraat 5", "16-05-1993",
                989384755L, 1000, 1000, 0L,
                "henk", "henk");
        Authentication incorrectObject = JSONParser.createJsonAuthentication("asds", AuthenticationType.LOGIN);
        String invalidJson = "accountNumber";
        try {
            ui.verifyNewAccountInput(jsonConverter.toJson(customer));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it should not have been thrown.");
        }
        try {
            Account account = new Account();
            account.setBalance(0);
            account.setSpendingLimit(0);
            customer.setAccount(account);
            ui.verifyNewAccountInput(jsonConverter.toJson(customer));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it should not have been thrown.");
        }
        try {
            customer.setInitials(null);
            ui.verifyNewAccountInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setInitials("B.D.");
        }
        try {
            customer.setName(null);
            ui.verifyNewAccountInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setName("Henk");
        }
        try {
            customer.setSurname(null);
            ui.verifyNewAccountInput(jsonConverter.toJson(customer));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            customer.setSurname("De weerd");
        }
        try {
            ui.verifyNewAccountInput(invalidJson);
            fail("JsonSyntax exception not thrown.");
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when a jsonSyntaxException should have been thrown.");
        } catch (JsonSyntaxException e) {
        }
        try {
            ui.verifyNewAccountInput(jsonConverter.toJson(incorrectObject));
            fail("IncorrectInputException not thrown.");
        } catch (IncorrectInputException e) {
        } catch (JsonSyntaxException e) {
            fail("JsonSyntaxException thrown when a IncorrectInputException should have been thrown.");
        }
    }

    @Test
    public void verifyLoginInput() {
        Authentication authentication = JSONParser.createJsonAuthenticationLogin("henk", "henk");
        DataRequest incorrectObject = JSONParser.createJsonDataRequest("NL00GNIB0000000000",
                                                                RequestType.ACCOUNTS, 0L);
        String invalidJson = "accountNumber";
        try {
            ui.verifyLoginInput(jsonConverter.toJson(authentication));
        } catch (IncorrectInputException e) {
            e.printStackTrace();
            fail("IncorrectInputException thrown when it should not have been thrown.");
        }
        try {
            authentication.setPassword(null);
            ui.verifyLoginInput(jsonConverter.toJson(authentication));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            authentication.setPassword("henk");
        }
        try {
            authentication.setUsername(null);
            ui.verifyLoginInput(jsonConverter.toJson(authentication));
            fail("IncorrectInputException not thrown when it should've been thrown.");
        } catch (IncorrectInputException e) {
            authentication.setUsername("henk");
        }
        try {
            ui.verifyLoginInput(invalidJson);
            fail("JsonSyntax exception not thrown.");
        } catch (IncorrectInputException e) {
            fail("IncorrectInputException thrown when a jsonSyntaxException should have been thrown.");
        } catch (JsonSyntaxException e) {
        }
        try {
            ui.verifyLoginInput(jsonConverter.toJson(incorrectObject));
            fail("IncorrectInputException not thrown.");
        } catch (IncorrectInputException e) {
        } catch (JsonSyntaxException e) {
            fail("JsonSyntaxException thrown when a IncorrectInputException should have been thrown.");
        }
    }
}
