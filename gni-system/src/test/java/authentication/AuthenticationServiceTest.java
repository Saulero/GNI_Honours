package authentication;

import database.*;
import databeans.Customer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import static database.SQLStatements.updateTokenValidity;
import static junit.framework.TestCase.*;

/**
 * @Author noel
 */
public class AuthenticationServiceTest {
/*
    private AuthenticationService authenticationService;
    private ConnectionPool databaseConnectionPool;

    @Before
    public void setUp() throws Exception {
        authenticationService = new AuthenticationService(0, "", 0, "");
        databaseConnectionPool = new ConnectionPool();
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement truncate = connection.getConnection().prepareCall(SQLStatements.truncateAuthTable);
        truncate.execute();
        truncate.close();
        databaseConnectionPool.returnConnection(connection);
    }

    @After
    public void tearDown() throws Exception {
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement truncate = connection.getConnection().prepareCall(SQLStatements.truncateAuthTable);
        truncate.execute();
        truncate.close();
        databaseConnectionPool.returnConnection(connection);
        authenticationService.shutdown();
    }



    @Test
    public void registerNewCustomerLoginTest() {
        Customer customer = new Customer();
        customer.setCustomerId(0L);
        customer.setPassword("henkie");
        customer.setUsername("henk123");
        try {
            authenticationService.registerNewCustomerLogin(customer);
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(SQLStatements.getAuthenticationData1);
            ps.setLong(1, customer.getCustomerId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                assertTrue(rs.getString("username").equals(customer.getUsername()));
                assertTrue(rs.getString("password").equals(customer.getPassword()));
            } else {
                fail("User login not in database.");
            }
            PreparedStatement ps2 = connection.getConnection().prepareStatement(SQLStatements.truncateAuthTable);
            ps2.execute();
            ps2.close();
            ps.close();
            databaseConnectionPool.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        }
    }

    @Test
    public void authenticateRequestTest() {
        Customer customer = new Customer();
        customer.setCustomerId(0L);
        customer.setPassword("henkie");
        customer.setUsername("henk123");
        String cookie = authenticationService.encodeCookie(0L, 2323456789L);
        try {
            authenticationService.registerNewCustomerLogin(customer);
            authenticationService.setNewToken(0L, 2323456789L);
            authenticationService.authenticateRequest(cookie);
        } catch (UserNotAuthorizedException | SQLException e) {
            e.printStackTrace();
            fail("Exception thrown when it should not be thrown.");
        }
        try {
            authenticationService.setNewToken(0L, 232345678910L);
            authenticationService.authenticateRequest(cookie);
            fail("UsernotAuthorizedException not thrown.");
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        } catch (UserNotAuthorizedException e) {
        }
        try {
            authenticationService.setNewToken(0L, 2323456789L);
            authenticationService.authenticateRequest(cookie);
        } catch (UserNotAuthorizedException | SQLException e) {
            e.printStackTrace();
            fail("Exception thrown when it should not be thrown.");
        }
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateTokenValidity);
            ps.setLong(1, System.currentTimeMillis() - 100000000L);    // validity
            ps.setLong(2, 0L);          // id
            ps.executeUpdate();
            ps.close();
            databaseConnectionPool.returnConnection(connection);
            authenticationService.authenticateRequest(cookie);
            fail("UsernotAuthorizedException not thrown.");
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        } catch (UserNotAuthorizedException e) {
        }
        try {
            authenticationService.updateTokenValidity(0L);
            authenticationService.authenticateRequest(cookie);
        } catch (SQLException | UserNotAuthorizedException e) {
            e.printStackTrace();
            fail("Exception thrown.");
        }
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(SQLStatements.truncateAuthTable);
            ps.execute();
            ps.close();
            authenticationService.authenticateRequest(cookie);
            fail("UsernotAuthorizedException not thrown.");
        } catch (SQLException e) {
            e.printStackTrace();
            fail("Exception thrown when it should not be thrown.");
        } catch (UserNotAuthorizedException e) {
        }
    }

    @Test
    public void updateTokenValidityTest() {
        Customer customer = new Customer();
        customer.setCustomerId(0L);
        customer.setPassword("henkie");
        customer.setUsername("henk123");
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            authenticationService.registerNewCustomerLogin(customer);
            authenticationService.setNewToken(0L, 2323456789L);
            PreparedStatement ps = connection.getConnection().prepareStatement(SQLStatements.updateTokenValidity);
            ps.setLong(1, 100L);
            ps.setLong(2, 0L);
            ps.execute();
            ps.close();
            PreparedStatement ps2 = connection.getConnection().prepareStatement(SQLStatements.getAuthenticationData2);
            ps2.setLong(1, 0L);
            ResultSet rs = ps2.executeQuery();
            if (rs.next()) {
                assertTrue(rs.getLong("token_validity") == 100L);
            } else {
                fail("Token_validity not set properly.");
            }
            ps2.close();
            authenticationService.updateTokenValidity(0L);
            PreparedStatement ps3 = connection.getConnection().prepareStatement(SQLStatements.getAuthenticationData2);
            ps3.setLong(1, 0L);
            ResultSet rs2 = ps3.executeQuery();
            if (rs2.next()) {
                assertTrue(rs2.getLong("token_validity") > System.currentTimeMillis() + Variables.TOKEN_VALIDITY * 900);
            } else {
                fail("Token_validity not set properly.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        }
    }

    @Test
    public void getCustomerIdTest() {
        Customer customer = new Customer();
        customer.setCustomerId(0L);
        customer.setPassword("henk");
        customer.setUsername("henk");
        String cookie = authenticationService.encodeCookie(0L, 123L);
        assertTrue(authenticationService.getCustomerId(cookie) == 0L);
    }

    @Test
    public void validateUsernameTest() {
        Customer customer = new Customer();
        customer.setCustomerId(0L);
        customer.setPassword("henk");
        customer.setUsername("henk");
        try {
            authenticationService.registerNewCustomerLogin(customer);
            authenticationService.validateUsername(customer);
            fail("UsernameTaken exception not thrown.");
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        } catch (UsernameTakenException e) {
        }
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(SQLStatements.truncateAuthTable);
            ps.execute();
            ps.close();
            databaseConnectionPool.returnConnection(connection);
            authenticationService.validateUsername(customer);
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        } catch (UsernameTakenException e) {
            e.printStackTrace();
            fail("UsernameTakenException thrown.");
        }
    }

    @Test
    public void encodeCookieTest() {
        Long userID = 0L;
        Long token = 123L;
        String cookie = authenticationService.encodeCookie(userID, token);
        Long[] decodedCookie = authenticationService.decodeCookie(cookie);
        assertTrue(decodedCookie[0].equals(userID));
        assertTrue(decodedCookie[1].equals(token));
        assertTrue(cookie.equals("" + userID + ":" + new String(Base64.getEncoder().encode(("" + token).getBytes()))));
    }

    @Test
    public void setNewTokenTest() {
        Customer customer = new Customer();
        customer.setCustomerId(0L);
        customer.setPassword("henkie");
        customer.setUsername("henk123");
        try {
            authenticationService.registerNewCustomerLogin(customer);
            authenticationService.setNewToken(0L, 2323456789L);
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(SQLStatements.getAuthenticationData1);
            ps.setLong(1, 0L);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                assertTrue(rs.getLong("token") == 2323456789L);
            } else {
                fail("No customer in database, so no token set either.");
            }
            authenticationService.setNewToken(0L, 123L);
            ResultSet rs2 = ps.executeQuery();
            if (rs2.next()) {
                assertTrue(rs2.getLong("token") == 123L);
            } else {
                fail("No customer in database, so no token set either.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            fail("Exception thrown when it should not be thrown.");
        }
    }
*/
}
