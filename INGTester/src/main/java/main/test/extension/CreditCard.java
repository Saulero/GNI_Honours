package main.test.extension;

import com.jayway.jsonpath.JsonPath;
import main.model.methods.*;
import main.test.BaseTest;
import main.util.AuthToken;
import main.util.Constants;
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static main.util.Checker.checkError;
import static main.util.Checker.checkSuccess;
import static main.util.ErrorCodes.*;
import static main.util.Methods.*;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class CreditCard extends BaseTest {

    /**
     * check request and close process
     */
    @Test
    public void requestCloseCheck() {
        //check if getBalance returns no credit field
        String result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "donald", "donald"), donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertThat(result, hasNoJsonPath("result.credit"));
        assertThat(result, hasJsonPath("result.length()", equalTo(1)));
        double initBalance = JsonPath.read(result, "result.balance");

        //request credit card
        result = client.processRequest(requestCreditCard, new RequestCreditCard(AuthToken.getAuthToken(client, "donald", "donald"), donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        assertThat(result, hasJsonPath("result.expirationDate"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        assertEquals(true, pinCard.matches("524886\\d{10}"));

        //simulate a day in order to let the credit card take effect
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //check if credit field is present in getBalance
        result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "donald", "donald"), donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertThat(result, hasJsonPath("result.credit"));
        assertThat(result, hasJsonPath("result.length()", equalTo(2)));

        //close credit card
        result = client.processRequest(closeAccount, new CloseAccount(AuthToken.getAuthToken(client, "donald", "donald"), donaldAccount.getiBAN() + "C"));
        checkSuccess(result);

        //balance should remain unchanged
        result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "donald", "donald"), donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(initBalance)));
    }

    /**
     * Daily use of credit card (paying and costs)
     */
    @Test
    public void transfer() {
        //simulate to first of month
        simulateToFirstOfMonth();

        //request credit Card for daisy
        String result = client.processRequest(requestCreditCard,
                new RequestCreditCard(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //simulate day in order to let the credit card take effect
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //pay 1.23 to donald
        result = client.processRequest(payFromAccount,
                new PayFromAccount(daisyAccount.getiBAN() + "C", donaldAccount.getiBAN(), pinCard, pinCode, 1.23));
        checkSuccess(result);

        //simulate to next of first month
        simulateToFirstOfMonth();

        //check if balance is -(1.23+5.00) and credit 1,000
        result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(-6.23)));
        assertThat(result, hasJsonPath("result.credit", equalTo(1000d)));

        //pay 10 to donald
        result = client.processRequest(payFromAccount,
                new PayFromAccount(daisyAccount.getiBAN() + "C", donaldAccount.getiBAN(), pinCard, pinCode, 10));
        checkSuccess(result);

        //close creditCard
        result = client.processRequest(closeAccount, new CloseAccount(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN() + "C"));
        checkSuccess(result);

        //check if balance is -6.23-10 and credit field is gone
        result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(-16.23)));
        assertThat(result, hasNoJsonPath("result.credit"));
    }

    /**
     * (mis)use of creditCard and request process
     */
    @Test
    public void misuse() {
        //make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 10000));
        checkSuccess(result);

        //request not authorized account request
        result = client.processRequest(requestCreditCard, new RequestCreditCard(AuthToken.getAuthToken(client, "donald", "donald"), dagobertAccount.getiBAN()));
        System.out.println("Expect fail: " + result);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //add dagobert to daisy account
        result = client.processRequest(provideAccess,
                new ProvideAccess(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN(), "dagobert"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));

        //try to request credit card with dagobert credentials
        result = client.processRequest(requestCreditCard, new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), daisyAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //remove dagobert from daisy account
        result = client.processRequest(revokeAccess,
                new RevokeAccessSelf(AuthToken.getAuthToken(client, "dagobert", "dagobert"), daisyAccount.getiBAN()));
        checkSuccess(result);

        //request unknown account number
        result = client.processRequest(requestCreditCard, new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), Constants.INVALID_IBAN));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //request credit card dagobert correctly
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //request again
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        checkError(result);

        //try to pay from not activated credit card
        PayFromAccount payFromAccountObject =
                new PayFromAccount(dagobertAccount.getiBAN() + "C", donaldAccount.getiBAN(), pinCard, pinCode, 1);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //simulate 1 day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //pay with wrong sourceaccount, creditcard combination
        payFromAccountObject.setSourceIBAN(donaldAccount.getiBAN());
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result);

        //pay from account to creditCard
        payFromAccountObject.setSourceIBAN(dagobertAccount.getiBAN());
        payFromAccountObject.setTargetIBAN(dagobertAccount.getiBAN() + "C");
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //pay more than 1000 in once
        payFromAccountObject.setSourceIBAN(payFromAccountObject.getTargetIBAN());
        payFromAccountObject.setTargetIBAN(donaldAccount.getiBAN());
        payFromAccountObject.setAmount(1000.01);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //pay 1000
        payFromAccountObject.setAmount(1000);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //try to pay more
        payFromAccountObject.setAmount(0.01);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //simulate to the first of next month
        simulateToFirstOfMonth();

        //check if credit is restored
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //pay three times with wrong pin
        payFromAccountObject.setPinCode("0");
        for (int i = 0; i < 3; i++) {
            result = client.processRequest(payFromAccount, payFromAccountObject);
            checkError(result, INVALID_PIN_ERROR);
        }

        //try to pay with correct pincode
        payFromAccountObject.setPinCode(pinCode);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result);

        //try to request credit card
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        checkError(result);

        //unblock credit card with wrong account
        UnblockCard unblockCardObject = new UnblockCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), donaldAccount.getiBAN(), pinCard);
        result = client.processRequest(unblockCard, unblockCardObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //unblock credit card with correct account
        unblockCardObject.setiBAN(dagobertAccount.getiBAN());
        result = client.processRequest(unblockCard, unblockCardObject);
        checkSuccess(result);

        //pay with credit card
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //invalidate credit card
        result = client.processRequest(invalidateCard,
                new InvalidateCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN(), pinCard, false));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasNoJsonPath("result.pinCode"));
        String newPinCard = JsonPath.read(result, "result.pinCard");
        assertEquals(true, newPinCard.matches("524886\\d{10}"));

        //try to pay with old credit card
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //try to pay with new credit card
        payFromAccountObject.setPinCard(newPinCard);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //pay with new card
        payFromAccountObject.setPinCard(newPinCard);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //close credit card account
        result = client.processRequest(closeAccount,
                new CloseAccount(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN() + "C"));
        checkSuccess(result);

        //try to pay with credit card
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * try to pay with expired card and request new one and pay with that one
     */
    @Test
    public void expiration() {
        //make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 1000000));
        checkSuccess(result);

        //request credit card
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //simulate 4000 days (roughly 11 years)
        result = client.processRequest(simulateTime, new SimulateTime(4000, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //try to pay with credit card
        result = client.processRequest(payFromAccount,
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), pinCard, pinCode, 1));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //get balance
        result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        double balance = JsonPath.read(result, "result.balance");

        //simulate till next month
        simulateToFirstOfMonth();

        //balance should be unchanged as a expired credit card should not cost any
        result = client.processRequest(getBalance, new GetBalance(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertEquals(balance, JsonPath.read(result, "result.balance"));

        //request new one
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        pinCard = JsonPath.read(result, "result.pinCard");
        pinCode = JsonPath.read(result, "result.pinCode");

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //pay with new one
        result = client.processRequest(payFromAccount,
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), pinCard, pinCode, 1));
        checkSuccess(result);

        //close credit card
        result = client.processRequest(closeAccount,
                new CloseAccount(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN() + "C"));
        checkSuccess(result);

    }


}
