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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

public class AdministrativeUserII extends BaseTest {

    /**
     * Administrative user is allowed to send money from each account
     */
    @Test
    public void transferMoney() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure donald has 1.23 funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 1.23));
        checkSuccess(result);

        //transfer money to dagobert
        TransferMoney transferMoneyObject =
                new TransferMoney(adminAuth, donaldAccount.getiBAN(), dagobertAccount.getiBAN(), "Receiver", 1.23, "Moving money");
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //check if money was transferred
        result = client.processRequest(getBalance, new GetBalance(adminAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(1.23)));

        //transfer money to daisy
        transferMoneyObject.setSourceIBAN(transferMoneyObject.getTargetIBAN());
        transferMoneyObject.setTargetIBAN(daisyAccount.getiBAN());
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //check if money was transferred
        result = client.processRequest(getBalance, new GetBalance(adminAuth, daisyAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(1.23)));

        //transfer back to donald
        transferMoneyObject.setSourceIBAN(transferMoneyObject.getTargetIBAN());
        transferMoneyObject.setTargetIBAN(donaldAccount.getiBAN());
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //check if money was transferred
        result = client.processRequest(getBalance, new GetBalance(adminAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance", equalTo(1.23)));

        //open savings account
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(donaldAuth, donaldAccount.getiBAN()));
        checkSuccess(result);

        //transfer to savings account
        transferMoneyObject.setSourceIBAN(transferMoneyObject.getTargetIBAN());
        transferMoneyObject.setTargetIBAN(donaldAccount.getiBAN() + "S");
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //transfer back to savings account
        transferMoneyObject.setSourceIBAN(transferMoneyObject.getTargetIBAN());
        transferMoneyObject.setTargetIBAN(donaldAccount.getiBAN());
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkSuccess(result);

        //close savings account
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN() + "S"));
        checkSuccess(result);
    }

    /**
     * Check if transfer account access works
     */
    @Test
    public void transferAccountAccess() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        // transfer dagobert account to daisy
        String result = client.processRequest(transferBankAccount, new TransferBankAccount(adminAuth, dagobertAccount.getiBAN(), "daisy"));
        checkSuccess(result);

        //check if daisy is main account holder
        result = client.processRequest(getUserAccess, new GetUserAccess(daisyAuth));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.length()", equalTo(2)));
        assertThat(result, hasJsonPath("result[?(@.owner == 'daisy')].iBAN", hasSize(2)));

        //check if dagobert has no accounts left
        result = client.processRequest(getUserAccess, new GetUserAccess(dagobertAuth));
        assertThat(result, hasJsonPath("result.length()", equalTo(0)));
        assertThat(result, hasNoJsonPath("error"));

        // provide access for donald to old dagoberts account
        result = client.processRequest(provideAccess, new ProvideAccess(daisyAuth, dagobertAccount.getiBAN(), "donald"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //transfer old dagobert account back to dagobert
        result = client.processRequest(transferBankAccount, new TransferBankAccount(adminAuth, dagobertAccount.getiBAN(), "dagobert"));
        checkSuccess(result);

        //check if donald still has access
        result = client.processRequest(getBankAccountAccess, new GetBankAccountAccess(dagobertAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result[?(@.username == 'dagobert')]"));
        assertThat(result, hasJsonPath("result[?(@.username == 'donald')]"));

        //remove donald access from dagobert account
        result = client.processRequest(revokeAccess, new RevokeAccess(dagobertAuth, dagobertAccount.getiBAN(), "donald"));
        checkSuccess(result);

    }

    /**
     * Checks wrong inputs for transferAccountAccess
     */
    @Test
    public void wrongTransferAccount() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //non admin auth
        String result = client.processRequest(transferBankAccount, new TransferBankAccount(donaldAuth, donaldAccount.getiBAN(), "daisy"));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //empty string authToken
        result = client.processRequest(transferBankAccount, new TransferBankAccount("", donaldAccount.getiBAN(), "daisy"));
        checkError(result, NOT_AUTHORIZED_ERROR);

        //wrong IBAN (is one shorter)
        result = client.processRequest(transferBankAccount, new TransferBankAccount(adminAuth, Constants.INVALID_IBAN, "daisy"));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //wrong username
        result = client.processRequest(transferBankAccount, new TransferBankAccount(adminAuth, donaldAccount.getiBAN(), "daily"));
        checkError(result, INVALID_PARAM_VALUE_ERROR);

        //username already main account holder
        result = client.processRequest(transferBankAccount, new TransferBankAccount(adminAuth, donaldAccount.getiBAN(), "donald"));
        checkError(result);
    }

    /**
     * checks frozen account
     */
    @Test
    public void freezeAccount() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure donald has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 10));
        checkSuccess(result);

        //open savings account
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));

        //make sure savings account has 1 fund
        result = client.processRequest(transferMoney,
                new TransferMoney(donaldAuth, donaldAccount.getiBAN(), donaldAccount.getiBAN() + "S", "me", 1, "To savings"));
        checkSuccess(result);

        //open credit card account
        result = client.processRequest(requestCreditCard, new RequestCreditCard(donaldAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String creditCard = JsonPath.read(result, "result.pinCard");
        String creditCardPin = JsonPath.read(result, "result.pinCode");

        //add daisy to donald account
        result = client.processRequest(provideAccess, new ProvideAccess(donaldAuth, donaldAccount.getiBAN(), "daisy"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        // freeze donald account
        SetFreezeUserAccount setFreezeUserAccountObject = new SetFreezeUserAccount(adminAuth, "donald", true);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkSuccess(result);

        //try to deposit money in frozen account
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(donaldAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 1));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to pay with frozen account
        result = client.processRequest(payFromAccount,
                new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), donaldAccount.getPinCard(), donaldAccount.getPinCode(), 1));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to pay with frozen account with daisy card
        result = client.processRequest(payFromAccount,
                new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), pinCard, pinCode, 1));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to transfer with frozen account
        result = client.processRequest(transferMoney,
                new TransferMoney(donaldAuth, donaldAccount.getiBAN(), dagobertAccount.getiBAN(), "Uncle", 1, "Booking"));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to transfer to savings with donald account
        TransferMoney transferMoneyObject =
                new TransferMoney(donaldAuth, donaldAccount.getiBAN(), donaldAccount.getiBAN() + "S", "me", 1, "Booking");
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to transfer to savings with daisy account
        transferMoneyObject.setAuthToken(daisyAuth);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to transfer from savings with daisy account
        transferMoneyObject.setSourceIBAN(transferMoneyObject.getTargetIBAN());
        transferMoneyObject.setTargetIBAN(donaldAccount.getiBAN());
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to transfer from savings with donald account
        transferMoneyObject.setAuthToken(donaldAuth);
        result = client.processRequest(transferMoney, transferMoneyObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to transfer with creditCard to dagobert
        result = client.processRequest(payFromAccount,
                new PayFromAccount(donaldAccount.getiBAN(), dagobertAccount.getiBAN(), creditCard, creditCardPin, 1));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //make sure dagobert has enough funds
        result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(dagobertAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 10));
        checkSuccess(result);

        //try to transfer to donald
        result = client.processRequest(transferMoney,
                new TransferMoney(dagobertAuth, dagobertAccount.getiBAN(), donaldAccount.getiBAN(), "Nephew", 1, "Loan"));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to pay to donald
        result = client.processRequest(payFromAccount,
                new PayFromAccount(dagobertAccount.getiBAN(), donaldAccount.getiBAN(), dagobertAccount.getPinCard(), dagobertAccount.getPinCode(), 1));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //set overdraft limit
        result = client.processRequest(setOverdraftLimit, new SetOverdraftLimit(donaldAuth, donaldAccount.getiBAN(), 100));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //invalidate a card
        InvalidateCard invalidateCardObject = new InvalidateCard(donaldAuth, donaldAccount.getiBAN(), donaldAccount.getPinCard(), false);
        result = client.processRequest(invalidateCard, invalidateCardObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        invalidateCardObject.setNewPin(true);
        result = client.processRequest(invalidateCard, invalidateCardObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //open savings account
        result = client.processRequest(openSavingsAccount, new OpenSavingsAccount(donaldAuth, donaldAccount.getiBAN()));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //setting transferlimit
        result = client.processRequest(setTransferLimit, new SetTransferLimit(donaldAuth, donaldAccount.getiBAN(), 100));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //request creditCard
        result = client.processRequest(requestCreditCard, new RequestCreditCard(donaldAuth, donaldAccount.getiBAN()));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //close account
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN()));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //open and additional account
        result = client.processRequest(openAdditionalAccount, new OpenAdditionalAccount(donaldAuth));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //provide access
        result = client.processRequest(provideAccess, new ProvideAccess(donaldAuth, donaldAccount.getiBAN(), "dagobert"));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //revoke daisy access
        RevokeAccess revokeAccessObject = new RevokeAccess(donaldAuth, donaldAccount.getiBAN(), "daisy");
        result = client.processRequest(revokeAccess, revokeAccessObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //daisy tries to cover own tracks
        revokeAccessObject.setAuthToken(daisyAuth);
        result = client.processRequest(revokeAccess, revokeAccessObject);
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to close savingsaccount
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN() + "S"));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to close creditCard
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN() + "C"));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //unfreeze donald account
        setFreezeUserAccountObject.setFreeze(false);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkSuccess(result);

        //revoke daisy
        result = client.processRequest(revokeAccess, new RevokeAccessSelf(daisyAuth, donaldAccount.getiBAN()));
        checkSuccess(result);

        //close savingsaccount
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN() + "S"));
        checkSuccess(result);

        //close creditCard
        result = client.processRequest(closeAccount, new CloseAccount(donaldAuth, donaldAccount.getiBAN() + "C"));
        checkSuccess(result);
    }

    /**
     * check frozen customer
     */
    @Test
    public void frozenCustomer() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        //make sure daisy has enough funds
        String result = client.processRequest(depositIntoAccount,
                new DepositIntoAccount(daisyAccount.getiBAN(), daisyAccount.getPinCard(), daisyAccount.getPinCode(), 10));
        checkSuccess(result);

        //provide donald access to daisy
        result = client.processRequest(provideAccess, new ProvideAccess(daisyAuth, daisyAccount.getiBAN(), "donald"));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        String pinCard = JsonPath.read(result, "result.pinCard");
        String pinCode = JsonPath.read(result, "result.pinCode");

        //freeze donald account
        SetFreezeUserAccount setFreezeUserAccountObject = new SetFreezeUserAccount(adminAuth, "donald", true);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkSuccess(result);

        //pay from daisy to dagobert with card of frozen customer
        result = client.processRequest(payFromAccount, new PayFromAccount(daisyAccount.getiBAN(), dagobertAccount.getiBAN(), pinCard, pinCode, 1));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //transfer from daisy to dagobert with frozen customer
        result = client.processRequest(transferMoney,
                new TransferMoney(donaldAuth, daisyAccount.getiBAN(), dagobertAccount.getiBAN(), "Uncle", 1, "White washing"));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //try to revoke access
        result = client.processRequest(revokeAccess, new RevokeAccessSelf(donaldAuth, daisyAccount.getiBAN()));
        checkError(result, ACCOUNT_FROZEN_ERROR);

        //unfreeze donald
        setFreezeUserAccountObject.setFreeze(false);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkSuccess(result);

        //revoke access of donald from daisy account
        result = client.processRequest(revokeAccess, new RevokeAccess(daisyAuth, daisyAccount.getiBAN(), "donald"));
        checkSuccess(result);
    }

    /**
     * Check invalid messages to setFreezeUserAccount
     */
    @Test
    public void freezeInvalidInput() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        SetFreezeUserAccount setFreezeUserAccountObject = new SetFreezeUserAccount(donaldAuth, "donald", true);

        //non admin freeze attempt
        setFreezeUserAccountObject.setAuthToken(daisyAuth);
        String result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //freeze account
        setFreezeUserAccountObject.setAuthToken(adminAuth);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkSuccess(result);

        //account already frozen
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkError(result, NO_EFFECT_ERROR);

        //non admin unfreeze attempt
        setFreezeUserAccountObject.setAuthToken(daisyAuth);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkError(result, NOT_AUTHORIZED_ERROR);

        //account already unfrozen
        setFreezeUserAccountObject.setAuthToken(adminAuth);
        setFreezeUserAccountObject.setFreeze(false);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkSuccess(result);

        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkError(result, NO_EFFECT_ERROR);

        //invalid IBAN
        setFreezeUserAccountObject.setFreeze(true);
        setFreezeUserAccountObject.setUsername(Constants.INVALID_IBAN);
        result = client.processRequest(setFreezeUserAccount, setFreezeUserAccountObject);
        checkError(result, INVALID_PARAM_VALUE_ERROR);
    }

}
