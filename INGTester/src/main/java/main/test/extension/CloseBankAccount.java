package main.test.extension;

import com.jayway.jsonpath.JsonPath;
import main.model.methods.*;
import main.test.BaseTest;
import main.util.AuthToken;
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static main.util.Checker.checkError;
import static main.util.Checker.checkSuccess;
import static main.util.ErrorCodes.*;
import static main.util.Methods.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * This test is part of the extension CreditCard. This test tests the closeAccount method.
 * This tests is run in a separate class as it (should) removes accounts which would influence other tests within that
 * class.
 */
public class CloseBankAccount extends BaseTest {

    /**
     * close credit and savings
     */
    @Test
    public void close() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //open credit
        String result = client.processRequest(requestCreditCard, new RequestCreditCard(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));

        //try to close normal account
        CloseAccount closeAccountObject = new CloseAccount(donaldAuth, donaldAccount.getiBAN());
        result = client.processRequest(closeAccount, closeAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //open savings
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(donaldAuth, donaldAccount.getiBAN()));
        checkSuccess(result);

        //try to close normal account
        result = client.processRequest(closeAccount, closeAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //close credit
        closeAccountObject.setiBAN(donaldAccount.getiBAN() + "C");
        result = client.processRequest(closeAccount, closeAccountObject);
        checkSuccess(result);

        //try to close again
        result = client.processRequest(closeAccount, closeAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //make sure donald has 1 fund
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 1));
        checkSuccess(result);

        //book to savings
        result = client.processRequest(transferMoney,
                new TransferMoney(donaldAuth, donaldAccount.getiBAN(), donaldAccount.getiBAN() + "S", "me", 1, "payment"));
        checkSuccess(result);

        //close savings
        closeAccountObject.setiBAN(donaldAccount.getiBAN() + "S");
        result = client.processRequest(closeAccount, closeAccountObject);
        checkSuccess(result);

        //try to close again
        result = client.processRequest(closeAccount, closeAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //check if money on savings is back on normal account
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertThat(result, hasNoJsonPath("result.credit"));
        assertEquals(1d, JsonPath.read(result, "result.balance"));

        //try to close donald account
        closeAccountObject.setiBAN(donaldAccount.getiBAN());
        result = client.processRequest(closeAccount, closeAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //pay 1 to daisy
        result = client.processRequest(payFromAccount,
                new PayFromAccount(donaldAccount.getiBAN(), daisyAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 1));
        checkSuccess(result);

        //close donald account
        result = client.processRequest(closeAccount, closeAccountObject);
        checkSuccess(result);

        //donald try to login
        result = client.processRequest(getAuthToken, new GetAuthToken("donald", "donald"));
        checkError(result, AUTHENTICATION_ERROR);
    }

}
