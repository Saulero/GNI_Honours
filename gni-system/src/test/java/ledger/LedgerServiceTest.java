package ledger;

import com.google.gson.Gson;
import database.SQLConnection;
import databeans.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.*;

/**
 * @author Saul
 */
public class LedgerServiceTest {

    private LedgerService ledger;
    private Gson gson;

    @Before
    public void setUp() throws Exception {
        ledger = new LedgerService();
        gson = new Gson();
    }

    @After
    public void tearDown() throws Exception {
        ledger.shutdown();
    }

    @Test
    public void createNewAccount() throws Exception {
        Account testAccount1 = new Account("TestName", 1000, 1000);
        testAccount1 = ledger.createNewAccount(null, gson.toJson(testAccount1));
        Account testAccount2 = ledger.getAccountInfo(testAccount1.getAccountNumber());
        assertEquals(testAccount1, testAccount2);

        SQLConnection con = new SQLConnection();
        PreparedStatement ps = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        ps.setString(1, testAccount1.getAccountNumber());

        assertEquals(1, ps.executeUpdate());

        ps.close();
        con.close();
    }

    @Test
    public void generateNewAccountNumber() throws Exception {
        Account test1 = new Account("Test. Name", 0, 0);
        Account test2 = new Account("Testname", 0, 0);
        Account test3 = new Account("Testnames", 0, 0);
        String accountNumber1 = ledger.generateNewAccountNumber(test1);
        String accountNumber2 = ledger.generateNewAccountNumber(test2);
        String accountNumber3 = ledger.generateNewAccountNumber(test3);

        assertEquals(accountNumber1, accountNumber2);
        assertNotEquals(accountNumber2, accountNumber3);
    }

    @Test
    public void attemptAccountNumberGeneration() throws Exception {
        String accountNumber1 = ledger.attemptAccountNumberGeneration("Test. Name", 0);
        String accountNumber2 = ledger.attemptAccountNumberGeneration("Test. Name", 1);
        String accountNumber3 = ledger.attemptAccountNumberGeneration("TestName", 0);
        String accountNumber4 = ledger.attemptAccountNumberGeneration("Testnames", 0);

        assertNotEquals(accountNumber1, accountNumber2);
        assertEquals(accountNumber1, accountNumber3);
        assertNotEquals(accountNumber1, accountNumber4);
    }

    @Test
    public void updateBalance() throws Exception {
        Account testAccount1 = new Account("TestName", 1000, 1000);
        testAccount1 = ledger.createNewAccount(null, gson.toJson(testAccount1));
        Account testAccount2 = ledger.getAccountInfo(testAccount1.getAccountNumber());

        assertEquals(testAccount1, testAccount2);

        testAccount1.setBalance(2000);
        testAccount1.setSpendingLimit(2000);
        ledger.updateBalance(testAccount1);
        Account testAccount3 = ledger.getAccountInfo(testAccount1.getAccountNumber());

        assertEquals(testAccount1, testAccount3);
        assertNotEquals(testAccount1, testAccount2);

        SQLConnection con = new SQLConnection();
        PreparedStatement ps = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        ps.setString(1, testAccount1.getAccountNumber());

        assertEquals(1, ps.executeUpdate());

        ps.close();
        con.close();
    }

    @Test
    public void addTransaction() throws Exception {
        Transaction transactionIn = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000000", "NL00GNIB0000000001", "TestName1", "TestDescription1", 50);
        transactionIn.generateTimestamp();
        Transaction transactionOut = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000002", "NL00GNIB0000000003", "TestName2", "TestDescription2", 50);
        transactionOut.generateTimestamp();

        SQLConnection con = new SQLConnection();

        PreparedStatement ps1 = con.getConnection().prepareStatement("SELECT * FROM transactions_in WHERE transactions_in.id = ?");
        ps1.setLong(1, transactionIn.getTransactionID());
        PreparedStatement ps2 = con.getConnection().prepareStatement("SELECT * FROM transactions_out WHERE transactions_out.id = ?");
        ps2.setLong(1, transactionOut.getTransactionID());
        ResultSet rs1 = ps1.executeQuery();
        ResultSet rs2 = ps2.executeQuery();

        assertFalse(rs1.next());
        assertFalse(rs2.next());

        ledger.addTransaction(transactionIn, true);
        ledger.addTransaction(transactionOut, false);
        rs1.close();
        rs2.close();
        rs1 = ps1.executeQuery();
        rs2 = ps2.executeQuery();

        assertTrue(rs1.next());
        assertTrue(rs2.next());

        rs1.close();
        rs2.close();
        ps1 = con.getConnection().prepareStatement("DELETE FROM transactions_in WHERE transactions_in.id = ?");
        ps1.setLong(1, transactionIn.getTransactionID());
        ps2 = con.getConnection().prepareStatement("DELETE FROM transactions_out WHERE transactions_out.id = ?");
        ps2.setLong(1, transactionOut.getTransactionID());

        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());

        ps1.close();
        ps2.close();
        con.close();
    }

    @Test
    public void getNextTransactionID() throws Exception {
        SQLConnection con = new SQLConnection();

        Transaction transactionIn = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000000", "NL00GNIB0000000001", "TestName1", "TestDescription1", 50);
        transactionIn.generateTimestamp();
        PreparedStatement ps = con.getConnection().prepareStatement("SELECT * FROM transactions_in WHERE transactions_in.id = ?");
        ps.setLong(1, transactionIn.getTransactionID());
        ResultSet rs = ps.executeQuery();
        assertFalse(rs.next());
        ledger.addTransaction(transactionIn, true);
        rs.close();
        rs = ps.executeQuery();
        assertTrue(rs.next());

        Transaction transactionOut = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000002", "NL00GNIB0000000003", "TestName2", "TestDescription2", 50);

        assertNotEquals(transactionIn.getTransactionID(), transactionOut.getTransactionID());
        assertEquals(transactionIn.getTransactionID() + 1, transactionOut.getTransactionID());

        ps = con.getConnection().prepareStatement("DELETE FROM transactions_in WHERE transactions_in.id = ?");
        ps.setLong(1, transactionIn.getTransactionID());
        ps.executeUpdate();

        ps.close();
        con.close();
    }

    @Test
    public void processIncomingTransaction() throws Exception {
        Account testAccount = new Account("TestName", 1000, 1000);
        testAccount = ledger.createNewAccount(null, gson.toJson(testAccount));
        Transaction transaction = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000000", testAccount.getAccountNumber(), testAccount.getAccountHolderName(), "TestDescription", 50);
        long id = transaction.getTransactionID();
        transaction = ledger.processIncomingTransaction(null, gson.toJson(transaction));

        assertTrue(transaction.isProcessed());
        assertTrue(transaction.isSuccessful());

        transaction = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000001", "NL00GNIB0000000002", "WrongName", "WrongDescription", 50);
        transaction = ledger.processIncomingTransaction(null, gson.toJson(transaction));

        assertTrue(transaction.isProcessed());
        assertFalse(transaction.isSuccessful());

        SQLConnection con = new SQLConnection();
        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM transactions_in WHERE transactions_in.id = ?");
        ps1.setString(1, testAccount.getAccountNumber());
        ps2.setLong(1, id);
        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());
    }

    @Test
    public void processOutgoingTransaction() throws Exception {
        Account testAccount = new Account("TestName", 1000, 1000);
        testAccount = ledger.createNewAccount(null, gson.toJson(testAccount));

        SQLConnection con = new SQLConnection();
        long customer_id = -1;
        PreparedStatement ps = con.getConnection().prepareStatement("INSERT INTO accounts (user_id, account_number) VALUES (?, ?)");
        ps.setLong(1, customer_id);
        ps.setString(2, testAccount.getAccountNumber());
        ps.executeUpdate();
        ps.close();

        Transaction transaction = new Transaction(ledger.getHighestTransactionID(), testAccount.getAccountNumber(), "NL00GNIB0000000000", "TestName", "TestDescription", 50);
        long transactionID = transaction.getTransactionID();
        transaction = ledger.processOutgoingTransaction(null, gson.toJson(transaction), "" + customer_id);

        assertTrue(transaction.isProcessed());
        assertTrue(transaction.isSuccessful());

        transaction = new Transaction(ledger.getHighestTransactionID(), testAccount.getAccountNumber(), "NL00GNIB0000000000", "TestName", "TestDescription", 2000);
        transaction = ledger.processIncomingTransaction(null, gson.toJson(transaction));

        assertTrue(transaction.isProcessed());
        assertFalse(transaction.isSuccessful());

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM transactions_out WHERE transactions_out.id = ?");
        PreparedStatement ps3 = con.getConnection().prepareStatement("DELETE FROM accounts WHERE accounts.user_id = ?");
        ps1.setString(1, testAccount.getAccountNumber());
        ps2.setLong(1, transactionID);
        ps3.setLong(1, customer_id);
        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());
        assertEquals(1, ps3.executeUpdate());
    }

    @Test
    public void processDataRequest() throws Exception {
        Account testAccount = new Account("TestName1", 1000, 1000);
        testAccount = ledger.createNewAccount(null, gson.toJson(testAccount));

        SQLConnection con = new SQLConnection();
        long customer_id = -1;
        PreparedStatement ps = con.getConnection().prepareStatement("INSERT INTO accounts (user_id, account_number) VALUES (?, ?)");
        ps.setLong(1, customer_id);
        ps.setString(2, testAccount.getAccountNumber());
        ps.executeUpdate();
        ps.close();

        DataRequest dataRequest1 = new DataRequest(testAccount.getAccountNumber(), RequestType.BALANCE, customer_id);
        DataRequest dataRequest2 = new DataRequest("NL00GNIB0000000000", RequestType.BALANCE, customer_id);
        DataReply dataReply1 = ledger.processDataRequest(null, gson.toJson(dataRequest1));
        DataReply dataReply2 = ledger.processDataRequest(null, gson.toJson(dataRequest2));

        assertNotNull(dataReply1);
        assertNull(dataReply2);
        assertEquals(testAccount, dataReply1.getAccountData());

        Transaction transactionIn = new Transaction(ledger.getHighestTransactionID(), "NL00GNIB0000000000", testAccount.getAccountNumber(), testAccount.getAccountHolderName(), "TestDescription1", 50);
        Transaction transactionOut = new Transaction(ledger.getHighestTransactionID(), testAccount.getAccountNumber(), "NL00GNIB0000000001", "TestName2", "TestDescription2", 50);
        transactionIn = ledger.processIncomingTransaction(null, gson.toJson(transactionIn));
        transactionOut = ledger.processOutgoingTransaction(null, gson.toJson(transactionOut), "" + customer_id);
        dataRequest1 = new DataRequest(testAccount.getAccountNumber(), RequestType.TRANSACTIONHISTORY, customer_id);

        dataReply1 = ledger.processDataRequest(null, gson.toJson(dataRequest1));

        assertEquals(2, dataReply1.getTransactions().size());
        System.out.println(transactionIn.toString());
        System.out.println(dataReply1.getTransactions().get(0));
        assertTrue(transactionIn.minimalEquals(dataReply1.getTransactions().get(0)));
        assertTrue(transactionOut.minimalEquals(dataReply1.getTransactions().get(1)));

        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM transactions_in WHERE transactions_in.id = ?");
        ps1.setLong(1, transactionIn.getTransactionID());
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM transactions_out WHERE transactions_out.id = ?");
        ps2.setLong(1, transactionOut.getTransactionID());
        PreparedStatement ps3 = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        ps3.setString(1, testAccount.getAccountNumber());
        PreparedStatement ps4 = con.getConnection().prepareStatement("DELETE FROM accounts WHERE accounts.user_id = ?");
        ps4.setLong(1, customer_id);

        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());
        assertEquals(1, ps3.executeUpdate());
        assertEquals(1, ps4.executeUpdate());

        ps1.close();
        ps2.close();
        ps3.close();
        con.close();
    }
}