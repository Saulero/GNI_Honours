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
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //check if getBalance returns no credit field
        String result = client.processRequest(getBalance, new GetBalance(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertThat(result, hasNoJsonPath("result.credit"));
        assertThat(result, hasJsonPath("result.length()", equalTo(1)));
        double initBalance = JsonPath.read(result, "result.balance");

        //request credit card
        result = client.processRequest(requestCreditCard, new RequestCreditCard(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        assertThat(result, hasJsonPath("result.expirationDate"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        assertEquals(true, pinCard.matches("524886\\d{10}"));

        //simulate a day in order to let the credit card take effect
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //check if credit field is present in getBalance
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertThat(result, hasJsonPath("result.credit"));
        assertThat(result, hasJsonPath("result.length()", equalTo(2)));

        //close credit card
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN() + "C"));
        checkSuccess(result);

        //balance should remain unchanged
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(initBalance)));
    }

    /**
     * Daily use of credit card (paying and costs)
     */
    @Test
    public void transfer() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //simulate to first of month
        simulateToFirstOfMonth(adminAuth);

        //request credit Card for daisy
        String result = client.processRequest(requestCreditCard,
                new RequestCreditCard(daisyAuth, daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //simulate day in order to let the credit card take effect
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //pay 1.23 to donald
        result = client.processRequest(payFromAccount,
                new PayFromAccount(daisyAccount.getiBAN() + "C", donaldAccount.getiBAN(), pinCard, pinCode, 1.23));
        checkSuccess(result);

        //simulate to next of first month
        simulateToFirstOfMonth(adminAuth);

        //check if balance is -(1.23+5.00) and credit 1,000
        result = client.processRequest(getBalance, new GetBalance(daisyAuth, daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(-6.23)));
        assertThat(result, hasJsonPath("result.credit", equalTo(1000d)));

        //pay 10 to donald
        result = client.processRequest(payFromAccount,
                new PayFromAccount(daisyAccount.getiBAN() + "C", donaldAccount.getiBAN(), pinCard, pinCode, 10));
        checkSuccess(result);

        //close creditCard
        result = client.processRequest(closeAccount, new CloseAccount(daisyAuth, daisyAccount.getiBAN() + "C"));
        checkSuccess(result);

        //check if balance is -6.23-10 and credit field is gone
        result = client.processRequest(getBalance, new GetBalance(daisyAuth, daisyAccount.getiBAN()));
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
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 10000));
        checkSuccess(result);

        //request not authorized account request
        result = client.processRequest(requestCreditCard, new RequestCreditCard(donaldAuth, dagobertAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //add dagobert to daisy account
        result = client.processRequest(provideAccess,
                new ProvideAccess(daisyAuth, daisyAccount.getiBAN(), "dagobert"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));

        //try to request credit card with dagobert credentials
        result = client.processRequest(requestCreditCard, new RequestCreditCard(dagobertAuth, daisyAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //remove dagobert from daisy account
        result = client.processRequest(revokeAccess,
                new RevokeAccessSelf(dagobertAuth, daisyAccount.getiBAN()));
        checkSuccess(result);

        //request unknown account number
        result = client.processRequest(requestCreditCard, new RequestCreditCard(dagobertAuth, Constants.INVALID_IBAN));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //request credit card dagobert correctly
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //request again
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(dagobertAuth, dagobertAccount.getiBAN()));
        checkError(result);

        //try to pay from not activated credit card
        PayFromAccount payFromAccountObject =
                new PayFromAccount(dagobertAccount.getiBAN() + "C", donaldAccount.getiBAN(), pinCard, pinCode, 1);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PIN_ERROR);

        //simulate 1 day
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //pay with wrong sourceaccount, creditcard combination
        payFromAccountObject.setSourceIBAN(donaldAccount.getiBAN());
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result);

        //pay from account to creditCard
        payFromAccountObject.setSourceIBAN(dagobertAccount.getiBAN());
        payFromAccountObject.setTargetIBAN(dagobertAccount.getiBAN() + "C");
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

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
        simulateToFirstOfMonth(adminAuth);

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
                new RequestCreditCard(dagobertAuth, dagobertAccount.getiBAN()));
        checkError(result);

        //unblock credit card with wrong account
        UnblockCard unblockCardObject = new UnblockCard(dagobertAuth, donaldAccount.getiBAN(), pinCard);
        result = client.processRequest(unblockCard, unblockCardObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //unblock credit card with correct account
        unblockCardObject.setiBAN(dagobertAccount.getiBAN());
        result = client.processRequest(unblockCard, unblockCardObject);
        checkSuccess(result);

        //pay with credit card
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //invalidate credit card
        result = client.processRequest(invalidateCard,
                new InvalidateCard(dagobertAuth, dagobertAccount.getiBAN(), pinCard, false));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasNoJsonPath("result.pinCode"));
        String newPinCard = JsonPath.read(result, "result.pinCard");
        assertEquals(true, newPinCard.matches("524886\\d{10}"));

        //try to pay with old credit card
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PIN_ERROR);

        //try to pay with new credit card
        payFromAccountObject.setPinCard(newPinCard);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PIN_ERROR);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //pay with new card
        payFromAccountObject.setPinCard(newPinCard);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //close credit card account
        result = client.processRequest(closeAccount,
                new CloseAccount(dagobertAuth, dagobertAccount.getiBAN() + "C"));
        checkSuccess(result);

        //try to pay with credit card
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PIN_ERROR);
    }

    /**
     * try to pay with expired card and request new one and pay with that one
     */
    @Test
    public void expiration() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 1000000));
        checkSuccess(result);

        //request credit card
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //simulate 4000 days (roughly 11 years)
        result = client.processRequest(simulateTime, new SimulateTime(4000, adminAuth));
        checkSuccess(result);

        //try to pay with credit card
        result = client.processRequest(payFromAccount,
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), pinCard, pinCode, 1));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //get balance
        result = client.processRequest(getBalance, new GetBalance(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        double balance = JsonPath.read(result, "result.balance");

        //simulate till next month
        simulateToFirstOfMonth(adminAuth);

        //balance should be unchanged as a expired credit card should not cost any
        result = client.processRequest(getBalance, new GetBalance(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        assertEquals(balance, JsonPath.read(result, "result.balance"));

        //request new one
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        pinCard = JsonPath.read(result, "result.pinCard");
        pinCode = JsonPath.read(result, "result.pinCode");

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //pay with new one
        result = client.processRequest(payFromAccount,
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), pinCard, pinCode, 1));
        checkSuccess(result);

        //close credit card
        result = client.processRequest(closeAccount,
                new CloseAccount(dagobertAuth, dagobertAccount.getiBAN() + "C"));
        checkSuccess(result);

    }


}
