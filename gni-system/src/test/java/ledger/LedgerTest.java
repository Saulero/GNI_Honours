package ledger;

import database.SQLConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.RequestType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.*;

/**
 * @author Saul
 */
public class LedgerTest {

    private Ledger ledger;

    @Before
    public void setUp() throws Exception {
        ledger = new Ledger();
    }

    @After
    public void tearDown() throws Exception {
        ledger.shutdown();
    }

    @Test
    public void createNewAccount() throws Exception {
        Account testAccount1 = new Account("TestName", 1000, 1000);
        ledger.createNewAccount(testAccount1);
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
        ledger.createNewAccount(testAccount1);
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
        Transaction transactionIn = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000000", "NL00GNIB0000000001", "TestName1", 50);
        transactionIn.generateTimestamp();
        Transaction transactionOut = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000002", "NL00GNIB0000000003", "TestName2", 50);
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

        Transaction transactionIn = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000000", "NL00GNIB0000000001", "TestName1", 50);
        transactionIn.generateTimestamp();
        PreparedStatement ps = con.getConnection().prepareStatement("SELECT * FROM transactions_in WHERE transactions_in.id = ?");
        ps.setLong(1, transactionIn.getTransactionID());
        ResultSet rs = ps.executeQuery();
        assertFalse(rs.next());
        ledger.addTransaction(transactionIn, true);
        rs.close();
        rs = ps.executeQuery();
        assertTrue(rs.next());

        Transaction transactionOut = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000002", "NL00GNIB0000000003", "TestName2", 50);

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
        ledger.createNewAccount(testAccount);
        Transaction transaction = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000000", testAccount.getAccountNumber(), testAccount.getAccountHolderName(), 50);
        long id = transaction.getTransactionID();
        ledger.processIncomingTransaction(transaction);

        assertTrue(transaction.isProcessed());
        assertTrue(transaction.isSuccessful());

        transaction = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000001", "NL00GNIB0000000002", "WrongName", 50);
        ledger.processIncomingTransaction(transaction);

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
        ledger.createNewAccount(testAccount);
        Transaction transaction = new Transaction(ledger.getNextTransactionID(), testAccount.getAccountNumber(), "NL00GNIB0000000000", "TestName", 50);
        long id = transaction.getTransactionID();
        ledger.processOutgoingTransaction(transaction);

        assertTrue(transaction.isProcessed());
        assertTrue(transaction.isSuccessful());

        transaction = new Transaction(ledger.getNextTransactionID(), testAccount.getAccountNumber(), "NL00GNIB0000000000", "TestName", 2000);
        ledger.processIncomingTransaction(transaction);

        assertTrue(transaction.isProcessed());
        assertFalse(transaction.isSuccessful());

        SQLConnection con = new SQLConnection();
        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM transactions_out WHERE transactions_out.id = ?");
        ps1.setString(1, testAccount.getAccountNumber());
        ps2.setLong(1, id);
        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());
    }

    @Test
    public void processDataRequest() throws Exception {
        Account testAccount = new Account("TestName", 1000, 1000);
        ledger.createNewAccount(testAccount);
        DataRequest dataRequest1 = new DataRequest(testAccount.getAccountNumber(), RequestType.BALANCE);
        DataRequest dataRequest2 = new DataRequest("NL00GNIB0000000000", RequestType.BALANCE);
        DataReply dataReply1 = ledger.processDataRequest(dataRequest1);
        DataReply dataReply2 = ledger.processDataRequest(dataRequest2);

        assertNotNull(dataReply1);
        assertNull(dataReply2);
        assertEquals(testAccount, dataReply1.getAccountData());

        Transaction transactionIn = new Transaction(ledger.getNextTransactionID(), "NL00GNIB0000000000", testAccount.getAccountNumber(), testAccount.getAccountHolderName(), 50);
        Transaction transactionOut = new Transaction(ledger.getNextTransactionID(), testAccount.getAccountNumber(), "NL00GNIB0000000001", "TestName2", 50);
        ledger.processIncomingTransaction(transactionIn);
        ledger.processOutgoingTransaction(transactionOut);
        dataRequest1 = new DataRequest(testAccount.getAccountNumber(), RequestType.TRANSACTIONHISTORY);
        dataRequest2 = new DataRequest("NL00GNIB0000000000", RequestType.TRANSACTIONHISTORY);

        dataReply1 = ledger.processDataRequest(dataRequest1);
        dataReply2 = ledger.processDataRequest(dataRequest2);

        assertEquals(2, dataReply1.getTransactions().size());
        assertEquals(0, dataReply2.getTransactions().size());
        assertTrue(transactionIn.minimalEquals(dataReply1.getTransactions().get(0)));
        assertTrue(transactionOut.minimalEquals(dataReply1.getTransactions().get(1)));

        SQLConnection con = new SQLConnection();
        PreparedStatement ps1 = con.getConnection().prepareStatement("DELETE FROM transactions_in WHERE transactions_in.id = ?");
        ps1.setLong(1, transactionIn.getTransactionID());
        PreparedStatement ps2 = con.getConnection().prepareStatement("DELETE FROM transactions_out WHERE transactions_out.id = ?");
        ps2.setLong(1, transactionOut.getTransactionID());
        PreparedStatement ps3 = con.getConnection().prepareStatement("DELETE FROM ledger WHERE ledger.account_number = ?");
        ps3.setString(1, testAccount.getAccountNumber());

        assertEquals(1, ps1.executeUpdate());
        assertEquals(1, ps2.executeUpdate());
        assertEquals(1, ps3.executeUpdate());

        ps1.close();
        ps2.close();
        ps3.close();
        con.close();
    }
}