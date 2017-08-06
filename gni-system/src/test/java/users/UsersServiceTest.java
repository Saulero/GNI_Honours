package users;

import database.SQLConnection;
import databeans.Customer;
import databeans.DataReply;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Saul
 */
public class UsersServiceTest {

    private UsersService users;

    @Before
    public void setUp() throws Exception {
        users = new UsersService();
    }

    @After
    public void tearDown() throws Exception {
        users.shutdown();
    }

/*    @Test
    public void accountLinkMethods() throws Exception {
        SQLConnection con = new SQLConnection();

        String acc1 = "TestString1Acc1";
        String acc2 = "TestString2Acc1";
        String acc3 = "TestString1Acc2";

        List<String> accounts1 = new ArrayList<>();
        List<String> accounts2 = new ArrayList<>();
        accounts1.add(acc1);
        accounts1.add(acc2);
        accounts2.add(acc3);

        users.linkAccountToCustomer(acc1, -1);
        users.linkAccountToCustomer(acc2, -1);
        users.linkAccountToCustomer(acc3, -2);

        assertTrue(users.getAccountLinkExistence(acc1, -1));
        assertTrue(users.getAccountLinkExistence(acc2, -1));
        assertTrue(users.getAccountLinkExistence(acc3, -2));
        assertFalse(users.getAccountLinkExistence(acc1, -2));
        assertFalse(users.getAccountLinkExistence(acc3, -1));
        assertFalse(users.getAccountLinkExistence(acc1, -3));
        assertFalse(users.getAccountLinkExistence("RandomString", -1));
        assertFalse(users.getAccountLinkExistence("RandomString", -3));

        DataReply reply1 = users.getCustomerAccounts(-1);
        DataReply reply2 = users.getCustomerAccounts(-2);
        DataReply reply3 = users.getCustomerAccounts(-3);

        assertTrue(reply1.getAccounts().containsAll(accounts1));
        assertTrue(reply2.getAccounts().containsAll(accounts2));
        assertTrue(reply3.getAccounts().isEmpty());

        reply1.getAccounts().retainAll(reply2.getAccounts());
        assertTrue(reply1.getAccounts().isEmpty());

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM accounts WHERE user_id = ?");
        ps1.setLong(1, -1);
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM accounts WHERE user_id = ?");
        ps2.setLong(1, -2);
        assertEquals(2, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());

        ps1.close();
        ps2.close();
        con.close();
    }*/

    @Test
    public void getCustomerData() throws Exception {
        SQLConnection con = new SQLConnection();

        Customer c1 = new Customer("Initials1", "Name1", "Surname1", "Email1", "Tel1", "Address1", "DOB1", -1, 1000, 1000, -1);
        Customer c2 = new Customer("Initials2", "Name2", "Surname2", "Email2", "Tel2", "Address2", "DOB2", -2, 1000, 1000, -2);

        users.enrollCustomer(c1);
        users.enrollCustomer(c2);

        Customer c11 = users.getCustomerData(c1.getCustomerId());
        Customer c22 = users.getCustomerData(c2.getCustomerId());

        assertTrue(c1.minimalEquals(c11));
        assertTrue(c2.minimalEquals(c22));
        assertFalse(c1.minimalEquals(c2));
        assertFalse(c11.minimalEquals(c22));

        try {
            users.getCustomerData(-3);
            fail();
        } catch (CustomerDoesNotExistException e) {
            // success
        }

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM users WHERE id = ?");
        ps1.setLong(1, -1);
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM users WHERE id = ?");
        ps2.setLong(1, -2);
        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());

        ps1.close();
        ps2.close();
        con.close();
    }

    @Test
    public void getNewCustomerId() throws Exception {
        SQLConnection con = new SQLConnection();

        long id1 = users.getNewCustomerId();
        long id11 = users.getNewCustomerId();
        Customer c1 = new Customer("Initials1", "Name1", "Surname1", "Email1", "Tel1", "Address1", "DOB1", -1, 1000, 1000, id1);

        users.enrollCustomer(c1);

        long id2 = users.getNewCustomerId();
        long id22 = users.getNewCustomerId();
        assertEquals(id1, id11);
        assertEquals(id2, id22);
        assertNotEquals(id1, id2);
        assertTrue(id2 > id1);

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM users WHERE id = ?");
        ps1.setLong(1, id1);
        assertEquals(1, ps1.executeUpdate());

        ps1.close();
        con.close();
    }

    @Test
    public void enrollCustomer() throws Exception {
        SQLConnection con = new SQLConnection();

        Customer c1 = new Customer("Initials1", "Name1", "Surname1", "Email1", "Tel1", "Address1", "DOB1", -1, 1000, 1000, -1);

        users.enrollCustomer(c1);
        Customer c11 = users.getCustomerData(c1.getCustomerId());

        assertTrue(c1.minimalEquals(c11));

        try {
            users.enrollCustomer(c1);
            fail();
        } catch (SQLException e) {
            // success
        }

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM users WHERE id = ?");
        ps1.setLong(1, c1.getCustomerId());
        assertEquals(1, ps1.executeUpdate());

        ps1.close();
        con.close();
    }

    @Test
    public void getCustomerExitence() throws Exception {
        SQLConnection con = new SQLConnection();

        Customer c1 = new Customer("Initials1", "Name1", "Surname1", "Email1", "Tel1", "Address1", "DOB1", -1, 1000, 1000, -1);

        users.enrollCustomer(c1);

        assertTrue(users.getCustomerExistence(c1.getCustomerId()));
        assertFalse(users.getCustomerExistence(-2));

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM users WHERE id = ?");
        ps1.setLong(1, c1.getCustomerId());
        assertEquals(1, ps1.executeUpdate());

        ps1.close();
        con.close();

        assertFalse(users.getCustomerExistence(c1.getCustomerId()));
    }
}
