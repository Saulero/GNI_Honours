package util;

import databeans.Customer;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.PinTransaction;
import databeans.RequestType;
import ledger.Account;
import ledger.Transaction;

/**
 * Created by noel on 18-2-17.
 * @author noel
 * @version 1
 */
public class JSONParser {

    public static DataReply createJsonReply(final String accountNumber, final RequestType type, final String data) {
        DataReply reply = new DataReply();
        reply.setAccountNumber(accountNumber);
        reply.setType(type);
        reply.setData(data);
        return reply;
    }

    public static DataRequest createJsonRequest(final String accountNumber, final RequestType type) {
        DataRequest request = new DataRequest();
        request.setType(type);
        request.setAccountNumber(accountNumber);
        return request;
    }

    public static Transaction createJsonTransaction(final long transactionID, final String sourceAccountNumber,
                                                    final String destinationAccountNumber,
                                                    final String destinationAccountHolderName,
                                                    final double transactionAmount, final boolean processed,
                                                    final boolean successfull) {
        Transaction transaction = new Transaction();
        transaction.setTransactionID(transactionID);
        transaction.setTimestamp(-1);
        transaction.setSourceAccountNumber(sourceAccountNumber);
        transaction.setDestinationAccountNumber(destinationAccountNumber);
        transaction.setDestinationAccountHolderName(destinationAccountHolderName);
        transaction.setTransactionAmount(transactionAmount);
        transaction.setProcessed(processed);
        transaction.setSuccessful(successfull);
        return transaction;
    }

    public static Customer createJsonCustomer(final String newInitials, final String newName, final String newSurname,
                                              final String newEmail, final String newTelephoneNumber,
                                              final String newAddress, final String newDob, final Long newSsn,
                                              final double newSpendingLimit, final double newBalance) {
        Customer customer = new Customer();
        customer.setInitials(newInitials);
        customer.setName(newName);
        customer.setSurname(newSurname);
        customer.setEmail(newEmail);
        customer.setTelephoneNumber(newTelephoneNumber);
        customer.setAddress(newAddress);
        customer.setDob(newDob);
        customer.setSsn(newSsn);
        customer.setAccount(new Account(newSurname, newSpendingLimit, newBalance));
        return customer;
    }

    public static PinTransaction createJsonPinTransaction(final String newSourceAccountNumber,
                                                          final String newDestinationAccountNumber,
                                                          final String newDestinationAccountHolderName,
                                                          final String newPinCode, final String newCardNumber,
                                                          final double newTransactionAmount) {
        PinTransaction pinTransaction = new PinTransaction();
        pinTransaction.setSourceAccountNumber(newSourceAccountNumber);
        pinTransaction.setDestinationAccountNumber(newDestinationAccountNumber);
        pinTransaction.setDestinationAccountHolderName(newDestinationAccountHolderName);
        pinTransaction.setPinCode(newPinCode);
        pinTransaction.setCardNumber(newCardNumber);
        pinTransaction.setTransactionAmount(newTransactionAmount);
        return pinTransaction;
    }
}
