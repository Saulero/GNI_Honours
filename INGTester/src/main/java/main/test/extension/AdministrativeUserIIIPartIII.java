package main.test.extension;

import main.model.methods.DepositIntoAccount;
import main.model.methods.OpenSavingsAccount;
import main.model.methods.SetValue;
import main.model.methods.TransferMoney;
import main.test.BaseTest;
import main.util.AuthToken;
import org.junit.Test;

import static main.util.Checker.checkSuccess;
import static main.util.Methods.*;
import static main.util.SystemVariableNames.*;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertThat;

/**
 * Tests the interest of the savings accounts
 */
public class AdministrativeUserIIIPartIII extends BaseTest {

    /**
     * interest tests
     */
    @Test
    public void savingsInterest() {
        //change interest rate 1 to 10%
        String dateStringNextDay = getDateStringNextDay();
        String result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), INTEREST_RATE_1, 0.1, dateStringNextDay));
        checkSuccess(result);

        //change interest rate 2 to 10%
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), INTEREST_RATE_2, 0.1, dateStringNextDay));
        checkSuccess(result);

        //change interest rate 3 to 10%
        dateStringNextDay = getDateStringNextDay();
        result = client.processRequest(setValue, new SetValue(AuthToken.getAdminLoginToken(client), INTEREST_RATE_3, 0.1, dateStringNextDay));
        checkSuccess(result);

        //simulate to first of year
        simulateToFirstOfYear();

        //reset authTokens
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");

        //open savings account for donald
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(donaldAuth, donaldAccount.getiBAN()));
        checkSuccess(result);

        //deposit 25k to donald
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 25000));
        checkSuccess(result);

        //book to savings of donald
        result = client.processRequest(transferMoney,
                new TransferMoney(donaldAuth, donaldAccount.getiBAN(), donaldAccount.getiBAN() + "S", "me", 25000, "Time to save"));
        checkSuccess(result);

        //open savings account for daisy
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(daisyAuth, daisyAccount.getiBAN()));
        checkSuccess(result);

        //deposit 50k to daisy
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(daisyAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 50000));
        checkSuccess(result);

        //book to savings
        result = client.processRequest(transferMoney,
                new TransferMoney(daisyAuth, daisyAccount.getiBAN(), daisyAccount.getiBAN() + "S", "me", 50000, "Time to save"));
        checkSuccess(result);

        //open savings of dagobert
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(dagobertAuth, dagobertAccount.getiBAN()));
        checkSuccess(result);

        //deposit 1M to dagobert account
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 1000000));
        checkSuccess(result);

        //book to savings
        result = client.processRequest(transferMoney,
                new TransferMoney(dagobertAuth, dagobertAccount.getiBAN(), dagobertAccount.getiBAN() + "S", "me", 1000000, "Time to save"));
        checkSuccess(result);

        //simulate year
        simulateToFirstOfYear();

        //check donald has balance of -+ 27.5k (1% offset allowed)
        double balance = getBalanceOfSavingsAccount(donaldAccount.getiBAN());
        assertThat(balance, closeTo(27500, 27500 * 0.01));

        //check daisy has balance of -+ 55k (1% offset allowed
        balance = getBalanceOfSavingsAccount(daisyAccount.getiBAN());
        assertThat(balance, closeTo(55000, 55000 * 0.01));

        //check dagobert has balance of 1.1M (1% offset allowed
        balance = getBalanceOfSavingsAccount(dagobertAccount.getiBAN());
        assertThat(balance, closeTo(1100000, 1000000 * 0.01));
    }


}
