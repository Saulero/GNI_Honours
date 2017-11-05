package main.test.extension;

import main.model.methods.*;
import main.test.BaseTest;
import main.util.AuthToken;
import org.junit.Test;

import static main.util.Checker.checkError;
import static main.util.Checker.checkSuccess;
import static main.util.ErrorCodes.INVALID_PARAM_VALUE_ERROR;
import static main.util.ErrorCodes.INVALID_PIN_ERROR;
import static main.util.ErrorCodes.NOT_AUTHORIZED_ERROR;
import static main.util.Methods.*;
import static main.util.SystemVariableNames.*;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;

public class AdministrativeUserIIIPartII extends BaseTest {

    /**
     * Card usage attempts
     */
    @Test
    public void cardUsageAttempts() {
        //make sure enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 100));
        checkSuccess(result);

        //set attempts to 4
        String dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), CARD_USAGE_ATTEMPTS, 4, dateStringNextDay));
        checkSuccess(result);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //try to pay 3 times with wrong pinCode
        PayFromAccount payFromAccountObject =
                new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), donaldAccount.getPinCard(), getInvalidPin(donaldAccount.getPinCode()), 1);
        for (int i = 0; i < 3; i++) {
            result = client.processRequest(payFromAccount, payFromAccountObject);
            checkError(result, INVALID_PIN_ERROR);
        }

        //pay normal
        payFromAccountObject.setPinCode(donaldAccount.getPinCode());
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //try to pay 4 times with wrong pin
        payFromAccountObject.setPinCode(getInvalidPin(donaldAccount.getPinCode()));
        for (int i = 0; i < 4; i++) {
            result = client.processRequest(payFromAccount, payFromAccountObject);
            checkError(result, INVALID_PIN_ERROR);
        }

        //try to pay normal
        payFromAccountObject.setPinCode(donaldAccount.getPinCode());
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //unblock card
        result = client.processRequest(unblockCard,
                new UnblockCard(AuthToken.getAuthToken(client, "donald", "donald"), donaldAccount.getiBAN(), donaldAccount.getPinCard()));
        checkSuccess(result);

        //try wrong format amount of decimals
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), CARD_USAGE_ATTEMPTS, 4.1, dateStringNextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * max overdraft limit
     */
    @Test
    public void maxOverdraftLimit() {
        //set max overdraft to 100
        String dateStringNextDay = getDateStringNextDay();
        String result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), MAX_OVERDRAFT_LIMIT, 100, dateStringNextDay));
        checkSuccess(result);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //try to set overdraft to 101
        result = client.processRequest(setOverdraftLimit,
                new SetOverdraftLimit(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN(), 101));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //set overdraft to 100
        result = client.processRequest(setOverdraftLimit,
                new SetOverdraftLimit(AuthToken.getAuthToken(client, "daisy", "daisy"), daisyAccount.getiBAN(), 100));
        checkSuccess(result);

        //try to pay 101 from daisy
        PayFromAccount payFromAccountObject =
                new PayFromAccount(daisyAccount.getiBAN(), donaldAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 101);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //pay 100 from daisy
        payFromAccountObject.setAmount(100);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //deposit 100 into daisy account
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(daisyAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 100));
        checkSuccess(result);

        //try wrong format amount of decimals
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), MAX_OVERDRAFT_LIMIT, 100.999, dateStringNextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * Overdraft interest
     */
    @Test
    public void overdraftInterest() {
        //set max overdraft to 1000
        String dateStringNextDay = getDateStringNextDay();
        String result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), MAX_OVERDRAFT_LIMIT, 1000, dateStringNextDay));
        checkSuccess(result);

        //set overdraft interest to 0.5
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), OVERDRAFT_INTEREST_RATE, 0.5, dateStringNextDay));
        checkSuccess(result);

        //simulate to first of month
        simulateToFirstOfMonth();

        //set overdraft to 1000
        result = client.processRequest(setOverdraftLimit,
                new SetOverdraftLimit(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN(), 1000));
        checkSuccess(result);

        //make sure dagobert has a balance of -1000 (pay to donald)
        result = client.processRequest(transferMoney,
                new TransferMoney(AuthToken.getAuthToken(client, "dagobert", "dagobert"),
                        dagobertAccount.getiBAN(), donaldAccount.getiBAN(), "Donald", 1000, "Loan"));
        checkSuccess(result);

        //simulate year
        result = client.processRequest(simulateTime, new SimulateTime(365, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //check if balance is +- 1500 (offset of 50 allowed)
        Double balance = getBalanceOfAccount(dagobertAccount.getiBAN());
        assertThat(balance, closeTo(balance, 50));
    }


}
