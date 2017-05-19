package pin;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;
import databeans.PinCard;
import databeans.PinTransaction;
import databeans.Transaction;
import org.junit.Before;
import org.junit.Test;
import ui.IncorrectInputException;
import util.JSONParser;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import static org.junit.Assert.*;

/**
 * @Author Noel
 */
public class PinTest {

    private PinService pin;
    private Gson jsonConverter;
    private ConnectionPool databaseConnectionPool;
    private static int accountNumberLength = 18;

    @Before
    public void setUp() throws Exception {
        pin = new PinService(0, "", 0, "");
        jsonConverter = new Gson();
        databaseConnectionPool = new ConnectionPool();
    }

    @Test
    public void generateExpirationDate() {
        Date date = new Date();
        date.setTime(date.getTime() + 154526400000L);
        assertTrue(pin.generateExpirationDate().after(date));
        assertFalse(pin.generateExpirationDate().before(date));
    }

    @Test
    public void getNextAvailableCardNumber() {
        try {
            Long cardNumber = pin.getNextAvailableCardNumber();
            Date expirationDate = pin.generateExpirationDate();
            String accountNumber = "NL52GNIB1234123412";
            String pinCode = "8888";
            Long customerId = 12345L;
            PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
            pin.addPinCardToDatabase(pinCard);
            Long newCardNumber = pin.getNextAvailableCardNumber();
            assertTrue(newCardNumber == (cardNumber + 1));
            PinCard pinCard2 = new PinCard(accountNumber, newCardNumber, pinCode,customerId, expirationDate);
            pin.addPinCardToDatabase(pinCard2);
            Long cardNumber2 = pin.getNextAvailableCardNumber();
            assertTrue(cardNumber2 == (cardNumber + 2));
            pin.deletePinCardFromDatabase(pinCard2);
            assertTrue(newCardNumber.equals(pin.getNextAvailableCardNumber()));
            pin.deletePinCardFromDatabase(pinCard);
            assertTrue(cardNumber.equals(pin.getNextAvailableCardNumber()));
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown");
        }
    }

    @Test
    public void addPinCardToDatabase() throws SQLException {
        Long cardNumber = pin.getNextAvailableCardNumber();
        Date expirationDate = pin.generateExpirationDate();
        String accountNumber = "NL52GNIB1234123412";
        String pinCode = "8888";
        Long customerId = 12345L;
        PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
        pin.addPinCardToDatabase(pinCard);
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCardInfo = databaseConnection.getConnection().prepareStatement(SQLStatements.getPinCard);
        getCardInfo.setLong(1, cardNumber);
        ResultSet cardInfo = getCardInfo.executeQuery();
        if (cardInfo.next()) {
            String message = "";
            if (!cardInfo.getString("account_number").equals(accountNumber)) {
                message = "AccountNumber not equal to value we wanted to set.";
            } else if (cardInfo.getLong("card_number") != cardNumber) {
                message = "Card number not equal to value we wanted to set.";
            } else if (!cardInfo.getString("pin_code").equals(pinCode)) {
                message = "pin code not equal to value we wanted to set.";
            } else if (cardInfo.getLong("user_id") != customerId) {
                message = "customerId not equal to value we wanted to set.";
            } else if (cardInfo.getDate("expiration_date").getTime() > (expirationDate.getTime() + 86400000)
                    || cardInfo.getDate("expiration_date").getTime() < (expirationDate.getTime() - 86400000)) {
                System.out.println(cardInfo.getDate("expiration_date").getTime());
                System.out.println(expirationDate.getTime());
                message = "exp_Date not equal to value we wanted to set.";
            }
            getCardInfo.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            pin.deletePinCardFromDatabase(pinCard);
            if (message.length() > 0) {
                fail(message);
            }
        } else {
            getCardInfo.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            fail("Card not added to database.");
        }
    }

    @Test
    public void deletePinCardFromDatabase() throws SQLException {
        Long cardNumber = pin.getNextAvailableCardNumber();
        Date expirationDate = pin.generateExpirationDate();
        String accountNumber = "NL52GNIB1234123412";
        String pinCode = "8888";
        Long customerId = 12345L;
        PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
        pin.addPinCardToDatabase(pinCard);
        pin.deletePinCardFromDatabase(pinCard);
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCardInfo = databaseConnection.getConnection().prepareStatement(SQLStatements.getPinCard);
        getCardInfo.setLong(1, cardNumber);
        ResultSet cardInfo = getCardInfo.executeQuery();
        if (cardInfo.next()) {
            cardInfo.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            fail("Card still in database.");
        }
        cardInfo.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    @Test
    public void createATMTransaction() {
        PinTransaction pinTransaction = JSONParser.createJsonPinTransaction("NL00GNIB0000000000",
                "NL00GNIB0000000022", "bob", "8888",
                1L, 20.00, true);
        assertTrue(pin.createATMTransaction(pinTransaction, "NL00GNIB0000000000").getDescription()
                                            .equals("ATM withdrawal card #" + pinTransaction.getCardNumber()));
        Transaction atmTransaction = pin.createATMTransaction(pinTransaction, "NL00GNIB0000000022");
        assertTrue(atmTransaction.getDescription().equals("ATM deposit card #" + pinTransaction.getCardNumber()));
        assertTrue(atmTransaction.getSourceAccountNumber().equals(pinTransaction.getSourceAccountNumber()));
        assertTrue(atmTransaction.getDestinationAccountNumber().equals(pinTransaction.getDestinationAccountNumber()));
        assertTrue(atmTransaction.getDestinationAccountHolderName().equals(pinTransaction.getDestinationAccountHolderName()));
        assertTrue(atmTransaction.getTransactionAmount() == pinTransaction.getTransactionAmount());
        assertFalse(atmTransaction.isProcessed());
        assertFalse(atmTransaction.isSuccessful());
    }

    @Test
    public void getATMTransactionAuthorization() {
        try {
            Long cardNumber = pin.getNextAvailableCardNumber();
            Date expirationDate = pin.generateExpirationDate();
            String accountNumber = "NL52GNIB1234123412";
            String pinCode = "8888";
            Long customerId = 12345L;
            PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
            pin.addPinCardToDatabase(pinCard);
            PinTransaction pinTransaction = JSONParser.createJsonPinTransaction( accountNumber,
                    "NL00GNIB0000000022", "bob", "8888",
                    cardNumber, 20.00, true);
            assertTrue(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber("NL00GNIB0000000022");
            pinTransaction.setDestinationAccountNumber(accountNumber);
            assertTrue(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber(accountNumber);
            assertFalse(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber("");
            assertFalse(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setDestinationAccountNumber("");
            assertFalse(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber("NL00GNIB0000000022");
            pinTransaction.setDestinationAccountNumber(accountNumber);
            assertTrue(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setPinCode("0000");
            assertFalse(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setPinCode("8888");
            assertTrue(pin.getATMTransactionAuthorization(pinTransaction));
            pinTransaction.setTransactionAmount(-20.0);
            assertFalse(pin.getATMTransactionAuthorization(pinTransaction));
            pin.deletePinCardFromDatabase(pinCard);
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        }
    }

    @Test
    public void getCustomerIdFromCardNumber() {
        try {
            Long cardNumber = pin.getNextAvailableCardNumber();
            Date expirationDate = pin.generateExpirationDate();
            String accountNumber = "NL52GNIB1234123412";
            String pinCode = "8888";
            Long customerId = 12345L;
            PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
            pin.addPinCardToDatabase(pinCard);
            assertTrue(customerId.equals(pin.getCustomerIdFromCardNumber(cardNumber)));
            pin.deletePinCardFromDatabase(pinCard);
            pin.getCustomerIdFromCardNumber(cardNumber);
            fail("IncorrectPinException not thrown.");
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        } catch (IncorrectPinException e) {
        }
    }

    @Test
    public void getPinTransactionAuthorization() {
        try {
            Long cardNumber = pin.getNextAvailableCardNumber();
            Date expirationDate = pin.generateExpirationDate();
            String accountNumber = "NL52GNIB1234123412";
            String pinCode = "8888";
            Long customerId = 12345L;
            PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
            pin.addPinCardToDatabase(pinCard);
            PinTransaction pinTransaction = JSONParser.createJsonPinTransaction( accountNumber,
                    "NL00GNIB0000000022", "bob", "8888",
                    cardNumber, 20.00, false);
            assertTrue(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber("NL00GNIB0000000022");
            assertFalse(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber(accountNumber);
            assertTrue(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber("");
            assertFalse(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setSourceAccountNumber(accountNumber);
            assertTrue(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setDestinationAccountNumber("");
            assertFalse(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setDestinationAccountNumber("NL00GNIB0000000022");
            assertTrue(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setPinCode("0000");
            assertFalse(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setPinCode("8888");
            assertTrue(pin.getPinTransactionAuthorization(pinTransaction));
            pinTransaction.setTransactionAmount(-20.0);
            assertFalse(pin.getPinTransactionAuthorization(pinTransaction));
            pin.deletePinCardFromDatabase(pinCard);
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        }
    }

    @Test
    public void getAccountNumberWithCardNumber() {
        try {
            Long cardNumber = pin.getNextAvailableCardNumber();
            Date expirationDate = pin.generateExpirationDate();
            String accountNumber = "NL52GNIB1234123412";
            String pinCode = "8888";
            Long customerId = 12345L;
            PinCard pinCard = new PinCard(accountNumber, cardNumber, pinCode,customerId, expirationDate);
            pin.addPinCardToDatabase(pinCard);
            assertTrue(accountNumber.equals(pin.getAccountNumberWithCardNumber(cardNumber)));
            pin.deletePinCardFromDatabase(pinCard);
            pin.getAccountNumberWithCardNumber(cardNumber);
            fail("IncorrectPinException not thrown.");
        } catch (SQLException e) {
            e.printStackTrace();
            fail("SQLException thrown.");
        } catch (IncorrectPinException e) {
        }
    }
}
