package main.test.extension;

import com.jayway.jsonpath.JsonPath;
import main.model.methods.*;
import main.test.BaseTest;
import main.util.AuthToken;
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static main.util.Checker.checkError;
import static main.util.Checker.checkSuccess;
import static main.util.ErrorCodes.INVALID_PARAM_VALUE_ERROR;
import static main.util.ErrorCodes.NOT_AUTHORIZED_ERROR;
import static main.util.Methods.*;
import static org.junit.Assert.assertThat;

public class SpendingLimits extends BaseTest {


    private String newPinCard;
    private String newPinCode;

    /**
     * Checks if the new transfer limit takes effect
     */
    @Test
    public void setWeeklyTransferLimit() {
        //make sure donald has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 10));
        checkSuccess(result);

        // set transfer limit to 1
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        result = client.processRequest(setTransferLimit, new SetTransferLimit(donaldAuth, donaldAccount.getiBAN(), 1));
        checkSuccess(result);

        //simulate day
        simulateDay();

        // pay 1 unit
        PayFromAccount payFromAccountObject = new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 1);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        // try to pay 1 unit again, should fail, limit was set to 1
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        // set limit to 2
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        result = client.processRequest(setTransferLimit, new SetTransferLimit(donaldAuth, donaldAccount.getiBAN(), 2));
        checkSuccess(result);

        //simulate day
        simulateDay();

        // pay 1
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        differentPayMethod();
    }

    /**
     * Test if the different pay methods stack for the weekly transfer limit
     */
    public void differentPayMethod() {
        // set limit to 3
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        String result = client.processRequest(setTransferLimit, new SetTransferLimit(donaldAuth, donaldAccount.getiBAN(), 3));
        checkSuccess(result);

        //simulate day
        simulateDay();

        // transfer 1
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        TransferMoney transferMoneyObject =
                new TransferMoney(donaldAuth, donaldAccount.getiBAN(), dagobertAccount.getiBAN(), "Grandpa", 1, "Yet another payment");
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        // transfer one again, should fail as limit is 3
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * Checks if the daily card limit takes effect
     */
    @Test
    public void dailyCardLimit() {
        // make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 1000));
        checkSuccess(result);

        // try to pay 300
        PayFromAccount payFromAccountObject =
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 300);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        // pay 249.99
        payFromAccountObject.setAmount(249.99);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        // try to pay 0.02
        payFromAccountObject.setAmount(0.02);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        // pay 0.01
        payFromAccountObject.setAmount(0.01);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        separateCardLimit();
        dayPassed();
    }

    /**
     * Checks if the card limit works per card
     */
    public void separateCardLimit() {
        // add new card to dagobert account
        String result = client.processRequest(provideAccess, new ProvideAccess(dagobertAuth, dagobertAccount.getiBAN(), "donald"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.pinCode"));
        assertThat(result, hasJsonPath("result.pinCard"));
        newPinCard = JsonPath.read(result, "result.pinCard");
        newPinCode = JsonPath.read(result, "result.pinCode");

        // pay 250
        PayFromAccount payFromAccountObject =
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), newPinCard, newPinCode, 250);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);
    }

    /**
     * Checks if day limit is restored
     */
    public void dayPassed() {
        // simulate day
        String result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        // pay 250 again
        PayFromAccount payFromAccountObject =
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 250);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);
    }

    /**
     * Checks lookback of transactions (lookback of 6 days).
     */
    @Test
    public void weeklyReset() {
        //set limit to 2500
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        String result = client.processRequest(setTransferLimit, new SetTransferLimit(daisyAuth, daisyAccount.getiBAN(), 2500));
        checkSuccess(result);

        //simulate day
        simulateDay();

        // make sure daisy has enough funds
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(daisyAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 100000));
        checkSuccess(result);

        // pay 2000, 500 limit remaining
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        TransferMoney transferMoneyObject =
                new TransferMoney(daisyAuth, daisyAccount.getiBAN(), dagobertAccount.getiBAN(), "Grandpa", 2000, "Payback loan");
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        // simulate one day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        // pay 500, 0 limit remaining
        transferMoneyObject.setAmount(500);
        transferMoneyObject.setAuthToken(AuthToken.getAuthToken(client, "daisy", "daisy"));
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        // any transaction for the coming 5 days should fail
        transferMoneyObject.setAmount(0.01);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        // simulate 5 days and check if daisy can't pay yet
        for (int i = 0; i < 5; i++) {
            result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
            checkSuccess(result);

            transferMoneyObject.setAuthToken(AuthToken.getAuthToken(client, "daisy", "daisy"));
            result = client.processRequest(transferMoney, transferMoneyObject);
            checkError(result, NOT_AUTHORIZED_ERROR);
        }

        //after this simulation daisy is allowed to transfer 2000
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        // daisy is allowed to transfer 2000 again
        transferMoneyObject.setAmount(2001);
        transferMoneyObject.setAuthToken(AuthToken.getAuthToken(client, "daisy", "daisy"));
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        // transfer 2000 again
        transferMoneyObject.setAmount(2000);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //simluate two months to make sure the weekly limit is restored
        simulateToFirstOfMonth();
        simulateToFirstOfMonth();
    }

    /**
     * Checks if the spending limit takes effect after simulating a day
     */
    @Test
    public void dayEffect(){
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");

        //make sure daisy has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(daisyAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 10));
        checkSuccess(result);


        //set limit to 1
        result = client.processRequest(setTransferLimit, new SetTransferLimit(daisyAuth, daisyAccount.getiBAN(), 1));
        checkSuccess(result);

        //simulate day
        simulateDay();

        //pay 1
        PayFromAccount payFromAccountObject = new PayFromAccount(daisyAccount.getiBAN(), dagobertAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 1);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //try to pay 1 again
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        //set limit to 2
        result = client.processRequest(setTransferLimit, new SetTransferLimit(daisyAuth, daisyAccount.getiBAN(), 2));
        checkSuccess(result);

        //try to pay 1 again
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //simulate day
        simulateDay();

        //pay 1 again
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);
    }

}
