package main.test.extension;

import com.jayway.jsonpath.JsonPath;
import main.model.BankAccount;
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
import static main.util.ErrorCodes.*;
import static main.util.Methods.*;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class ChildAccount extends BaseTest {

    /**
     * add child normal,
     * try to add new child with child as guardian,
     * try to add with one old enough one child,
     * add with two guardians,
     * check access,
     * check interest,
     * check 18 year switch
     */
    @Test
    public void childAccount() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //get date and retrieve birthday of 10 year olds
        Calendar calendar = getDate();
        calendar.add(Calendar.YEAR, -10);
        String birthDayString = CalendarUtil.sdf.format(calendar.getTime());

        //add new minor kwik
        OpenAccountGuardian openAccountGuardian =
                new OpenAccountGuardian("Kwik", "Duck", "K.", birthDayString, "1261561", "Somewhere", "06519159624", "kwik@gmail.com", "kwik", "young", "child", new String[]{"donald"});
        String result = client.processRequest(openAccount, openAccountGuardian);
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCode"));
        assertThat(result, hasJsonPath("result.pinCard"));
        BankAccount kwikAccount = new BankAccount();
        kwikAccount.setiBAN((String) JsonPath.read(result, "result.iBAN"));
        kwikAccount.setPinCard((String) JsonPath.read(result, "result.pinCard"));
        kwikAccount.setPinCode((String) JsonPath.read(result, "result.pinCode"));

        //try to add kwak with kwik as gaurdian
        openAccountGuardian.setName("Kwak");
        openAccountGuardian.setEmail("Kwak@gmail.com");
        openAccountGuardian.setGuardians(new String[]{"kwik"});
        openAccountGuardian.setUsername("kwak");
        result = client.processRequest(openAccount, openAccountGuardian);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //try to add kwak with kwak as guardian
        openAccountGuardian.setGuardians(new String[]{"kwak"});
        result = client.processRequest(openAccount, openAccountGuardian);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //try to add kwak with kwik and donald as guardians
        openAccountGuardian.setGuardians(new String[]{"kwik", "donald"});
        result = client.processRequest(openAccount, openAccountGuardian);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //add kwak with donald and dagobert as guardians
        openAccountGuardian.setGuardians(new String[]{"dagobert", "donald"});
        result = client.processRequest(openAccount, openAccountGuardian);
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCode"));
        assertThat(result, hasJsonPath("result.pinCard"));
        BankAccount kwakAccount = new BankAccount();
        kwakAccount.setiBAN((String) JsonPath.read(result, "result.iBAN"));
        kwakAccount.setPinCard((String) JsonPath.read(result, "result.pinCard"));
        kwakAccount.setPinCode((String) JsonPath.read(result, "result.pinCode"));

        //check kwik access
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, kwikAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //try dagobert access kwik
        result = client.processRequest(getBalance, new GetBalance(dagobertAuth, kwikAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //check kwak access donald
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, kwakAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //check kwak access dagobert
        result = client.processRequest(getBalance, new GetBalance(dagobertAuth, kwakAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //simulate till first of next year
        simulateToFirstOfYear(adminAuth);

        //try to pay from kwak 1 to donald (account not allowed to go negative)
        result = client.processRequest(payFromAccount,
                new PayFromAccount(kwakAccount.getiBAN(), donaldAccount.getiBAN(), kwakAccount.getPinCard(), kwakAccount.getPinCode(), 1));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //deposit 1000 into kwak account
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(kwakAccount.getiBAN(), kwakAccount.getPinCard(), kwakAccount.getPinCode(), 1000));
        checkSuccess(result);

        //deposit 10000 into kwik account
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(kwikAccount.getiBAN(), kwikAccount.getPinCard(), kwikAccount.getPinCode(), 10000));
        checkSuccess(result);

        //simulate year
        simulateToFirstOfYear(adminAuth);

        //check kwak balance (1% offset allowed)
        double balance = getBalanceOfAccount(kwakAccount.getiBAN());
        assertThat(balance, closeTo(1.02017 * 1000, 0.01 * 1.02017 * 1000));

        //check kwik account balance (only until 2.5k should be calculated)
        balance = getBalanceOfAccount(kwikAccount.getiBAN());
        assertThat(balance, closeTo(0.02017 * 2500 + 10000, 0.02017 * 2500 * 0.1));

        //retrieve kwak auth
        String kwakAuth = AuthToken.getAuthToken(client, "kwak", "young");

        //try to request savings account
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(kwakAuth, kwakAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //try to request credit account
        result = client.processRequest(requestCreditCard, new RequestCreditCard(kwakAuth, kwakAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //try to setOverdraft limit to 10
        result = client.processRequest(setOverdraftLimit, new SetOverdraftLimit(kwakAuth, kwakAccount.getiBAN(), 10));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //try to provide access to daisy
        result = client.processRequest(provideAccess, new ProvideAccess(kwakAuth, kwakAccount.getiBAN(), "daisy"));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //try to revoke access of donald
        result = client.processRequest(revokeAccess, new RevokeAccess(kwakAuth, kwakAccount.getiBAN(), "donald"));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //try to open additional account
        result = client.processRequest(openAdditionalAccount, new OpenAdditionalAccount(kwakAuth));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //simulate 8 times until first of next year
        for (int i = 0; i < 8; i++) {
            simulateToFirstOfYear(adminAuth);
        }
        //check access of kwak of donald
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, kwakAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //check access of kwik of donald
        result = client.processRequest(getBalance, new GetBalance(donaldAuth, kwikAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //check access of kwik of dagobert
        result = client.processRequest(getBalance, new GetBalance(dagobertAuth, kwikAccount.getiBAN()));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //get balance of kwak
        balance = getBalanceOfAccount(kwakAccount.getiBAN());

        //simulate year
        simulateToFirstOfYear(adminAuth);

        //check if balance remained unchanged (account should not receive interest anymore)
        assertThat(getBalanceOfAccount(kwakAccount.getiBAN()), equalTo(balance));

        //get kwak auth
        kwakAuth = AuthToken.getAuthToken(client, "kwak", "young");

        //request savings
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(kwakAuth, kwakAccount.getiBAN()));
        checkSuccess(result);

        //request creditCard
        result = client.processRequest(requestCreditCard, new RequestCreditCard(kwakAuth, kwakAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //provide access to daisy
        result = client.processRequest(provideAccess, new ProvideAccess(kwakAuth, kwakAccount.getiBAN(), "daisy"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //revoke access to daisy
        result = client.processRequest(revokeAccess, new RevokeAccess(kwakAuth, kwakAccount.getiBAN(), "daisy"));
        checkSuccess(result);
    }

    /**
     * invalid use of openAccount
     */
    @Test
    public void invalidUse() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        OpenAccountGuardian openAccountGuardian =
                new OpenAccountGuardian("Kwek", "Duck", "K.", "1990-1-1", "1261561", "Somewhere", "06519159624", "kwik@gmail.com", "kwek", "young", "child", new String[]{"donald"});
        //open child account with non minor
        String result = client.processRequest(openAccount, openAccountGuardian);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //open child account with no guardians
        Calendar calendar = getDate();
        calendar.add(Calendar.YEAR, -10);
        String birthDayString = CalendarUtil.sdf.format(calendar.getTime());
        openAccountGuardian.setDob(birthDayString);
        openAccountGuardian.setGuardians(new String[]{});
        result = client.processRequest(openAccount, openAccountGuardian);
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //open child account that turns today 18 years
        calendar.add(Calendar.YEAR, -8);
        birthDayString = CalendarUtil.sdf.format(calendar.getTime());
        openAccountGuardian.setGuardians(new String[]{"donald"});
        openAccountGuardian.setDob(birthDayString);
        result = client.processRequest(openAccount, openAccountGuardian);
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }
}
