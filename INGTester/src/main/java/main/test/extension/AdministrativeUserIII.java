package main.test.extension;

import com.jayway.jsonpath.JsonPath;
import main.model.methods.*;
import main.test.BaseTest;
import main.util.AuthToken;
import main.util.CalendarUtil;
import org.junit.Test;

import java.util.Calendar;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static main.util.Checker.checkError;
import static main.util.Checker.checkSuccess;
import static main.util.ErrorCodes.INVALID_PARAM_VALUE_ERROR;
import static main.util.Methods.*;
import static main.util.SystemVariableNames.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * This class contains tests for the first 4 system variables. The rest of the tests can be found in AdministrativeUserIIIPartII
 */
public class AdministrativeUserIII extends BaseTest {


    /**
     * credit card monthly fee
     */
    @Test
    public void monthlyFee() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //simulate to first of month
        simulateToFirstOfMonth(adminAuth);

        //make sure dagobert has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 100));
        checkSuccess(result);

        //retrieve balance
        double balance = getBalanceOfAccount(dagobertAccount.getiBAN());

        //request credit card
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //set monthly fee to take effect next day
        String dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(adminAuth, CREDIT_CARD_MONTHLY_FEE,9.99, dateStringNextDay));
        checkSuccess(result);

        //simulate till next month
        simulateToFirstOfMonth(adminAuth);

        //check if balance is balance-9.99
        assertThat(balance - 9.99, equalTo(getBalanceOfAccount(dagobertAccount.getiBAN())));

        //set value with to much decimals
        result = client.processRequest(setValue, new SetValue(adminAuth, CREDIT_CARD_MONTHLY_FEE,9.999, getDateStringNextDay()));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

    }

    /**
     * credit card credit
     */
    @Test
    public void monthlyCredit() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure donald has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 100));
        checkSuccess(result);


        //set limit to 50
        String dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(adminAuth, CREDIT_CARD_DEFAULT_CREDIT, 50, dateStringNextDay));
        checkSuccess(result);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //request credit card
        result = client.processRequest(requestCreditCard,
                new RequestCreditCard(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //try to pay 50.01
        PayFromAccount payFromAccountObject =
                new PayFromAccount(donaldAccount.getiBAN() + "C", dagobertAccount.getiBAN(), pinCard, pinCode, 50.01);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //try to pay 50
        payFromAccountObject.setAmount(50);
        result = client.processRequest(payFromAccount, payFromAccountObject);
        checkSuccess(result);

        //set value with to much decimals
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(adminAuth, CREDIT_CARD_MONTHLY_FEE, 49.999, dateStringNextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * Card expiration length
     */
    @Test
    public void expirationLength() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //set length to 2 years
        String dateStringNextDay = getDateStringNextDay();
        String result = client.processRequest(setValue, new SetValue(adminAuth, CARD_EXPIRATION_LENGTH, 2, dateStringNextDay));
        checkSuccess(result);

        //simulate day
        result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);

        //request additional account and check expiration date of card
        result = client.processRequest(openAdditionalAccount, new OpenAdditionalAccount(daisyAuth));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        assertThat(result, hasJsonPath("result.expirationDate"));
        String newAccount = JsonPath.read(result, "result.iBAN");
        Calendar expirationCalendar = CalendarUtil.getCalenderOfString((String) JsonPath.read(result, "result.expirationDate"));


        //request current date
        result = client.processRequest(getDate, new GetDate(adminAuth));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.date"));
        Calendar currentCalendar = CalendarUtil.getCalenderOfString((String) JsonPath.read(result, "result.date"));
        assertEquals(2, expirationCalendar.get(Calendar.YEAR) - currentCalendar.get(Calendar.YEAR));

        //request creditCard
        result = client.processRequest(requestCreditCard, new RequestCreditCard(daisyAuth, daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));

        //close creditCard
        result = client.processRequest(closeAccount, new CloseAccount(daisyAuth, daisyAccount.getiBAN() + "C"));
        checkSuccess(result);

        //close additional account
        result = client.processRequest(closeAccount, new CloseAccount(daisyAuth, newAccount));
        checkSuccess(result);

        //try to set with to much decimals
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(adminAuth, CARD_EXPIRATION_LENGTH, 2.1, dateStringNextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

    /**
     * new card costs
     */
    @Test
    public void newCardCosts() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure daisy has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(daisyAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 100));
        checkSuccess(result);

        //request creditCard
        result = client.processRequest(requestCreditCard, new RequestCreditCard(daisyAuth, daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        String pinCard = JsonPath.read(result, "result.pinCard");

        //set new card costs to 1.50
        String dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(adminAuth, NEW_CARD_COST, 1.50, dateStringNextDay));
        checkSuccess(result);

        //simulate to first of month
        simulateToFirstOfMonth(adminAuth);

        //get balance
        double balance = getBalanceOfAccount(daisyAccount.getiBAN());

        //replace card
        result = client.processRequest(invalidateCard,
                new InvalidateCard(daisyAuth, daisyAccount.getiBAN(), daisyAccount.getPinCard(), false));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //get balance
        double newBalance = getBalanceOfAccount(daisyAccount.getiBAN());
        assertThat(balance - newBalance, equalTo(1.5));

        //replace credit card
        result = client.processRequest(invalidateCard,
                new InvalidateCard(daisyAuth, daisyAccount.getiBAN(), pinCard, false));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //get balance
        balance = newBalance;
        newBalance = getBalanceOfAccount(daisyAccount.getiBAN());
        assertThat(balance - newBalance, equalTo(1.5));

        //close credit account
        result = client.processRequest(closeAccount, new CloseAccount(daisyAuth, daisyAccount.getiBAN() + "C"));
        checkSuccess(result);

        //try to set with to much decimals
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(adminAuth, NEW_CARD_COST, 2.112, dateStringNextDay));
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

}
