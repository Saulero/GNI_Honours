package main.test;

import com.jayway.jsonpath.JsonPath;
import main.TestSuite;
import main.client.IClient;
import main.model.BankAccount;
import main.model.methods.*;
import main.util.AuthToken;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static main.util.CalendarUtil.*;
import static main.util.Checker.checkSuccess;
import static main.util.Methods.*;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@Ignore
public class BaseTest {

    public static IClient client = TestSuite.client;

    public static BankAccount donaldAccount;
    public static BankAccount dagobertAccount;
    public static BankAccount daisyAccount;

    public String donaldAuth = AuthToken.getAuthToken(client, "donald", "donald");
    public String dagobertAuth = AuthToken.getAuthToken(client, "dagobert", "dagobert");
    public String daisyAuth = AuthToken.getAuthToken(client, "daisy", "daisy");
    public String adminAuth = AuthToken.getAdminLoginToken(client);

    @BeforeClass
    public static void setUp() {
        String authToken = AuthToken.getAdminLoginToken(client);
        String result = client.processRequest(simulateTime, new SimulateTime(1, authToken));
        assertThat(result, hasJsonPath("result.length()", equalTo(0)));
        addDefaultUsers();
    }

    @AfterClass
    public static void reset() {
        String authToken = AuthToken.getAdminLoginToken(client);
        String result = client.processRequest(reset, new Reset(authToken));
        assertThat(result, hasJsonPath("result.length()", equalTo(0)));
    }

    public static void addDefaultUsers() {
        OpenAccount openAccountObject = new OpenAccount("Donald", "Duck", "D.", "1934-6-9", "123456789", "1313 Webfoot Walk, Duckburg", "+316 12345678", "donald@gmail.com", "donald", "donald");
        String result = client.processRequest(openAccount, openAccountObject);
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));
        donaldAccount = new BankAccount();
        donaldAccount.setiBAN((String) JsonPath.read(result, "result.iBAN"));
        donaldAccount.setPinCard((String) JsonPath.read(result, "result.pinCard"));
        donaldAccount.setPinCode((String) JsonPath.read(result, "result.pinCode"));

        openAccountObject.setName("Dagobert");
        openAccountObject.setSsn("123456798");
        openAccountObject.setEmail("dagobert@gamil.com");
        openAccountObject.setUsername("dagobert");
        openAccountObject.setPassword("dagobert");

        result = client.processRequest(openAccount, openAccountObject);
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));

        dagobertAccount = new BankAccount();
        dagobertAccount.setiBAN((String) JsonPath.read(result, "result.iBAN"));
        dagobertAccount.setPinCard((String) JsonPath.read(result, "result.pinCard"));
        dagobertAccount.setPinCode((String) JsonPath.read(result, "result.pinCode"));

        openAccountObject.setName("Daisy");
        openAccountObject.setSsn("123456799");
        openAccountObject.setEmail("daisy@gamil.com");
        openAccountObject.setUsername("daisy");
        openAccountObject.setPassword("daisy");

        result = client.processRequest(openAccount, openAccountObject);
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasJsonPath("result.iBAN"));
        assertThat(result, hasJsonPath("result.pinCard"));
        assertThat(result, hasJsonPath("result.pinCode"));

        daisyAccount = new BankAccount();
        daisyAccount.setiBAN("" + JsonPath.read(result, "result.iBAN"));
        daisyAccount.setPinCard((String) JsonPath.read(result, "result.pinCard"));
        daisyAccount.setPinCode((String) JsonPath.read(result, "result.pinCode"));
    }

    /**
     * Retrieves the balance of the given IBAN. It retrieves the balance via the admin login
     *
     * @param IBAN of the account from which to retrieve the balance
     * @return balance of the account
     */
    public double getBalanceOfAccount(String IBAN) {
        String result = client.processRequest(getBalance, new GetBalance(adminAuth, IBAN));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.balance"));
        return JsonPath.read(result, "result.balance");
    }

    /**
     * Retrieves the savings balance of the given IBAN. It retrieves the balance via the admin login
     *
     * @param IBAN of the account from which to retrieve the savings balance
     * @return balance of the savings account
     */
    public double getBalanceOfSavingsAccount(String IBAN) {
        String result = client.processRequest(getBalance, new GetBalance(adminAuth, IBAN));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.savingAccountBalance"));
        return JsonPath.read(result, "result.savingAccountBalance");
    }

    /**
     * Simulates to the first next first of the month.
     */
    public void simulateToFirstOfMonth(String adminToken) {
        String result = client.processRequest(getDate, new GetDate(adminToken));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.date"));
        Calendar calendar = getCalenderOfString((String) JsonPath.read(result, "result.date"));

        //simulate the days
        result = client.processRequest(simulateTime, new SimulateTime(getDaysTillNextFirstOfMonth(calendar), adminToken));
        checkSuccess(result);
    }

    public void simulateToFirstOfYear(String adminToken) {
        Calendar date = getDate();
        Calendar target = Calendar.getInstance();
        target.setTime(date.getTime());
        target.add(Calendar.YEAR, 1);
        target.set(Calendar.DAY_OF_MONTH, 1);
        target.set(Calendar.MONTH, 0);
        int days = (int) TimeUnit.DAYS.convert(target.getTimeInMillis() - date.getTimeInMillis(), TimeUnit.MILLISECONDS);
        //simulate the days
        String result = client.processRequest(simulateTime, new SimulateTime(days, adminToken));
        checkSuccess(result);
    }

    /**
     * Simulates a single day using a new retrieved admin auth token.
     */
    public void simulateDay(){
        String result = client.processRequest(simulateTime, new SimulateTime(1, adminAuth));
        checkSuccess(result);
    }

    /**
     * retrieves the date of the server
     *
     * @return calender object
     */
    public Calendar getDate() {
        String result = client.processRequest(getDate, new GetDate(adminAuth));
        assertThat(result, hasJsonPath("result"));
        assertThat(result, hasNoJsonPath("error"));
        assertThat(result, hasJsonPath("result.date"));
        return getCalenderOfString((String) JsonPath.read(result, "result.date"));
    }

    /**
     * Returns the date string of the next day on the server in the format yyyy-MM-dd
     *
     * @return date string representing the next day on the server
     */
    public String getDateStringNextDay() {
        Calendar calendar = getDate();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return sdf.format(calendar.getTime());
    }

    /**
     * Returns invalid pin.
     *
     * @param pinCode
     * @return
     */
    public String getInvalidPin(String pinCode) {
        if (pinCode.equals("0000")) {
            return "0001";
        }
        return "0000";
    }


}
