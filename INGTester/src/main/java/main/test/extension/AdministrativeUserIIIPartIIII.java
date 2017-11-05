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
import static main.util.ErrorCodes.INVALID_PARAM_VALUE_ERROR;
import static main.util.ErrorCodes.NOT_AUTHORIZED_ERROR;
import static main.util.Methods.*;
import static main.util.SystemVariableNames.DAILY_WITHDRAW_LIMIT;
import static main.util.SystemVariableNames.WEEKLY_TRANSFER_LIMIT;
import static org.junit.Assert.assertThat;

/**
 * Transfer limit tests
 */
public class AdministrativeUserIIIPartIIII extends BaseTest {

    /**
     * Test daily limit
     */
    @Test
    public void dailyLimit() {
        //set daily transfer limit to 50
        String dateStringNextDay = getDateStringNextDay();
        String result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), DAILY_WITHDRAW_LIMIT, 50, dateStringNextDay));
        checkSuccess(result);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //make sure donald has enough funds
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 100));
        checkSuccess(result);

        //try to transfer 50.01
        result = client.processRequest(payFromAccount,
                new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 50.01));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //pay 50
        result = client.processRequest(payFromAccount,
                new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 50));
        checkSuccess(result);

        //try to setValue with to much decimals
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), DAILY_WITHDRAW_LIMIT, 50.111, dateStringNextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * Test weekly limit (only takes effect for new accounts
     */
    @Test
    public void weeklyLimit() {
        //make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 10000));
        checkSuccess(result);

        //set weekly limit to 100
        String dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), WEEKLY_TRANSFER_LIMIT, 100, dateStringNextDay));
        checkSuccess(result);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //make new account
        result = client.processRequest(openAccount,
                new OpenAccount("New", "User", "N.", "1990-05-01", "123456123", "Somewhere", "0658742385", "user@gmail.com", "user", "user"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String iBAN = JsonPath.read(result, "result.iBAN");
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //make sure user has enough funds
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(iBAN, pinCard, pinCode, 1000));
        checkSuccess(result);

        //try to pay 101
        TransferMoney transferMoneyObject =
                new TransferMoney(AuthToken.getAuthToken(client, "user", "user"), iBAN, daisyAccount.getiBAN(), "Daisy", 101, "Money");
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //pay 100
        transferMoneyObject.setAmount(100);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //pay 1000 with dagobert
        TransferMoney dagoberTransfer =
                new TransferMoney(AuthToken.getAuthToken(client, "dagobert", "dagobert"), dagobertAccount.getiBAN(), daisyAccount.getiBAN(), "Daisy", 1000, "Something");
        result = client.processRequest(transferMoney, dagoberTransfer);
        checkSuccess(result);

        //try to pay 0.01
        transferMoneyObject.setAmount(0.01);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //simulate week
        result = client.processRequest(simulateTime, new SimulateTime(7, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);

        //try to pay 101 again
        transferMoneyObject.setAmount(101);
        transferMoneyObject.setAuthToken(AuthToken.getAuthToken(client, "user", "user"));
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //pay 100
        transferMoneyObject.setAmount(100);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //pay 1000 again with dagobert
        dagoberTransfer.setAuthToken(AuthToken.getAuthToken(client, "dagobert", "dagobert"));
        result = client.processRequest(transferMoney, dagoberTransfer);
        checkSuccess(result);
    }

    /**
     * Test invalid use of setValue
     */
    @Test
    public void invalidUse() {
        //today date
        String nextDay = getDateStringNextDay();
        //simulate day
        String result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), WEEKLY_TRANSFER_LIMIT, 100, nextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //yesterday date
        result = client.processRequest(simulateTime, new SimulateTime(1, AuthToken.getAdminLoginToken(client)));
        checkSuccess(result);
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), WEEKLY_TRANSFER_LIMIT, 100, nextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //unknown key
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), "limit", 100, nextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //no admin login
        result = client.processRequest(setValue, new SetValue(AuthToken.getAuthToken(client, "donald", "donald"), WEEKLY_TRANSFER_LIMIT, 100, nextDay));
        checkError(result, NOT_AUTHORIZED_ERROR);
    }

}
