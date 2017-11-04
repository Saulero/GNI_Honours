package main;

import main.client.IClient;
import main.client.TestHttpClient;
import main.test.extension.*;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({ChildAccount.class,
        AdministrativeUserIII.class, AdministrativeUserIIIPartII.class, AdministrativeUserIIIPartIII.class, AdministrativeUserIIIPartIIII.class,
        CloseBankAccount.class,
        AdministrativeUserIII.class,
        CreditCard.class,
        AdministrativeUserII.class,
        AdministrativeUser.class,
        SpendingLimits.class})
public class TestSuite {

    //TODO change to http or socket client
    public static IClient client = new TestHttpClient("http://localhost:9997/services/api/request");
//    public static IClient client = new SocketClient();


    @AfterClass
    public static void printRequestCount() {
        System.out.println(String.format("Test done executing. %s request were sent.", client.getRequestCount()));
    }


}
