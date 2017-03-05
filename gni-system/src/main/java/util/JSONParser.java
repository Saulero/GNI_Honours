package util;

import databeans.*;

/**
 * Creates objects by initializing them using empty constructors and then setting all variables. Needed to use the
 * objects as Json objects.
 * @author Noel
 * @version 1
 */
public final class JSONParser {

    /**
     * Private constructor for utility class.
     */
    private JSONParser() {
        //Not called
    }

    /**
     * Creates a DataReply object that can be converted to Json.
     * @param accountNumber Account number the reply is for.
     * @param type Type of request that this reply is for.
     * @param account Account data of the reply.
     * @return DataReply object that can be converted to a Json String.
     */
    public static DataReply createJsonReply(final String accountNumber, final RequestType type, final Account account) {
        DataReply reply = new DataReply();
        reply.setAccountNumber(accountNumber);
        reply.setType(type);
        reply.setAccountData(account);
        return reply;
    }

    /**
     * Creates a DataRequest object that can be converted to Json.
     * @param accountNumber Account number the request is for.
     * @param type Type of request.
     * @param newUserId User id the request is for.
     * @return DataRequest object that can be converted to a Json String.
     */
    public static DataRequest createJsonRequest(final String accountNumber, final RequestType type,
                                                final int newUserId) {
        DataRequest request = new DataRequest();
        request.setType(type);
        request.setAccountNumber(accountNumber);
        request.setUserId(newUserId);
        return request;
    }

    /**
     * Creates a Transaction object that can be converted to a Json String.
     * @param transactionID Transaction number.
     * @param sourceAccountNumber Account number funds are pulled from.
     * @param destinationAccountNumber Account number funds are transferred to.
     * @param destinationAccountHolderName Name of the destination account owner.
     * @param transactionAmount Amount of money that is transferred.
     * @param processed Indicates if the transaction has been processed by the ledger.
     * @param successfull Indicates if the transaction was successfull.
     * @return Transaction object that can be converted to Json.
     */
    public static Transaction createJsonTransaction(final long transactionID, final String sourceAccountNumber,
                                                    final String destinationAccountNumber,
                                                    final String destinationAccountHolderName,
                                                    final String description,
                                                    final double transactionAmount, final boolean processed,
                                                    final boolean successfull) {
        Transaction transaction = new Transaction();
        transaction.setTransactionID(transactionID);
        transaction.setTimestamp(-1);
        transaction.setSourceAccountNumber(sourceAccountNumber);
        transaction.setDestinationAccountNumber(destinationAccountNumber);
        transaction.setDestinationAccountHolderName(destinationAccountHolderName);
        transaction.setDescription(description);
        transaction.setTransactionAmount(transactionAmount);
        transaction.setProcessed(processed);
        transaction.setSuccessful(successfull);
        return transaction;
    }

    /**
     * Creates a Customer object that can be converted to a Json String.
     * @param newInitials Initials of the customer.
     * @param newName First name of the customer.
     * @param newSurname Last name of the customer.
     * @param newEmail Email of the customer.
     * @param newTelephoneNumber Telephone number of the customer.
     * @param newAddress Address of the customer.
     * @param newDob Date of birth of the customer.
     * @param newSsn Social security number of the customer.
     * @param newSpendingLimit Spending limit of the customers account(used for creating a new account).
     * @param newBalance Balance of the customers account(used for creating a new account).
     * @return A Customer object that can be converted to a Json String.
     */
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

    /**
     * Creates a PinTransaction object that can be converted to a Json String.
     * @param newSourceAccountNumber The account the funds of the transaction will be pulled from.
     * @param newDestinationAccountNumber The account the funds of the transaction will be deposited into.
     * @param newDestinationAccountHolderName The name of the destination account owner.
     * @param newPinCode The pincode used by the source account holder.
     * @param newCardNumber The card number of the card used to do the pinBa transaction.
     * @param newTransactionAmount The amount the transaction is for.
     * @return A PinTransaction object that can be converted to a Json String.
     */
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

    public static Account createJsonAccount(final String newAccountNumber, final double newSpendingLimit,
                                            final double newBalance) {
        Account account = new Account();
        account.setAccountNumber(newAccountNumber);
        account.setSpendingLimit(newSpendingLimit);
        account.setBalance(newBalance);
        return account;
    }

    public static AccountLink createJsonAccountLink(final Long newCustomerId, final String newAccountNumber) {
        AccountLink request = new AccountLink();
        request.setCustomerId(newCustomerId);
        request.setAccount(createJsonAccount(newAccountNumber, 0, 0));
        request.setSuccessfull(false);
        return request;
    }
}
