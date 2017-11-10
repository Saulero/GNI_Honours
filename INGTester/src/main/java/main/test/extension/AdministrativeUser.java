package main.test.extension;

import main.model.methods.*;
import main.test.BaseTest;
import main.util.AuthToken;
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static main.util.ErrorCodes.NOT_AUTHORIZED_ERROR;
import static main.util.Methods.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class AdministrativeUser extends BaseTest {
    /**
     * check if admin is allowed to execute getBalance
     */
    @Test
    public void getBalance() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        String result = client.processRequest(getBalance, new GetBalance(adminAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.balance"));

        result = client.processRequest(getBalance, new GetBalance(adminAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.balance"));
    }

    /**
     * check if admin is allowed to retrieve transaction overviews
     */
    @Test
    public void getTransaction() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        String result = client.processRequest(getTransactionsOverview, new GetTransactionsOverview(adminAuth, donaldAccount.getiBAN(), 1));
        assertThat(result, hasJsonPath("result.length()", equalTo(0)));
        assertThat(result, hasNoJsonPath("error"));

        result = client.processRequest(getTransactionsOverview, new GetTransactionsOverview(adminAuth, dagobertAccount.getiBAN(), 1));
        assertThat(result, hasJsonPath("result.length()", equalTo(0)));
        assertThat(result, hasNoJsonPath("error"));
    }

    /**
     * checks if admin is allowed to retrieve who has access to certain bank accounts
     */
    @Test
    public void getBankAccountAccess() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        String result = client.processRequest(getBankAccountAccess, new GetBankAccountAccess(adminAuth, donaldAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.length()", equalTo(1)));

        result = client.processRequest(getBankAccountAccess, new GetBankAccountAccess(adminAuth, dagobertAccount.getiBAN()));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.length()", equalTo(1)));
    }

    /**
     * checks if non admin can not execute simulateTime and reset
     */
    @Test
    public void invalidAccess() {
        //log users in.
        adminAuth = AuthToken.getAdminLoginToken(client);
        donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
        daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
        dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
        String result = client.processRequest(simulateTime, new SimulateTime(1, donaldAuth));
        assertThat(result, hasNoJsonPath("result"));
        assertThat(result, hasJsonPath("error"));
        assertThat(result, hasJsonPath("error.code", equalTo(NOT_AUTHORIZED_ERROR)));

        result = client.processRequest(reset, new Reset(donaldAuth));
        assertThat(result, hasNoJsonPath("result"));
        assertThat(result, hasJsonPath("error"));
        assertThat(result, hasJsonPath("error.code", equalTo(NOT_AUTHORIZED_ERROR)));
    }


}
