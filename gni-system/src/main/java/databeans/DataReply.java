package databeans;

import java.io.Serializable;

import java.util.List;

/**
 * @author Noel
 * @version 2
 * Databean used to send a reply to another service.
 */
//TODO needs to be reworked to not be dependent on accountNumber
public final class DataReply implements Serializable {
    /** Account number the reply corresponds to. */
    private String accountNumber;
    /** The type of request that this reply is for. */
    private RequestType type;
    /** The data of the reply, in case it was a Balance request. */
    private Account accountData;
    /** The data of the reply, in case it was a transaction history request. */
    private List<Transaction> transactions;
    /** The data of the reply, in case it was an accounts request. */
    private List<String> accounts;
    /** The data of the reply, in case it was an account exists request. */
    private boolean accountInLedger;

    /**
     * Creates a DataReply to send to other microservices.
     * @param newAccountNumber Account number the reply corresponds to.
     * @param newType Type of request that this reply is for.
     * @param newAccountData Data of the reply.
     */
    public DataReply(final String newAccountNumber, final RequestType newType, final Account newAccountData) {
        this.accountNumber = newAccountNumber;
        this.type = newType;
        this.accountData = newAccountData;
        this.transactions = null;
        this.accounts = null;
    }

    /**
     * Creates a DataReply object for a transaction history request.
     * @param newAccountNumber Account number the reply corresponds to.
     * @param newType Type of request that this reply is for.
     * @param newTransactions Data of the reply.
     */
    public DataReply(final String newAccountNumber, final RequestType newType, final List<Transaction> newTransactions) {
        this.accountNumber = newAccountNumber;
        this.type = newType;
        this.accountData = null;
        this.transactions = newTransactions;
    }

    /**
     * Creates a DataReply object for a account numbers request.
     * @param newType Type of request that this reply is for.
     * @param newAccounts Account numbers that belong to the requestee.
     */
    public DataReply(final RequestType newType, final List<String> newAccounts) {
        this.accountNumber = null;
        this.type = newType;
        this.accountData = null;
        this.transactions = null;
        this.accounts = newAccounts;
    }

    /**
     * Creates a DataReply object for a account exists request.
     * @param newType Type of the request that this reply is for.
     * @param newAccountNumber Account number of the request that this reply is for.
     * @param newAccountInLedger Boolean indicating if the account exists in the ledger.
     */
    public DataReply(final RequestType newType, final String newAccountNumber, final boolean newAccountInLedger) {
        this.type = newType;
        this.accountNumber = newAccountNumber;
        this.accountInLedger = newAccountInLedger;
    }

    /**
     * Empty constructor for json conversion of the object. Do not use as manual constructor.
     */
    public DataReply() { }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        this.accountNumber = newAccountNumber;
    }

    public RequestType getType() {
        return type;
    }

    public void setType(final RequestType newType) {
        this.type = newType;
    }

    public Account getAccountData() {
        return accountData;
    }

    public void setAccountData(final Account newAccountData) {
        this.accountData = newAccountData;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(final List<Transaction> newTransactions) {
        this.transactions = newTransactions;
    }

    public List<String> getAccounts() {
        return accounts;
    }

    public void setAccounts(final List<String> newAccountNumbers) {
        accounts = newAccountNumbers;
    }

    public boolean isAccountInLedger() {
        return accountInLedger;
    }

    public void setAccountInLedger(final boolean newAccountExists) {
        accountInLedger = newAccountExists;
    }
}
