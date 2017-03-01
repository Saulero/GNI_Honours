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
    /** The data of the reply, in case it was a accountdata request. */
    private Account accountData;
    /** The data of the reply, in case it was a transactionhistory request. */
    private List<Transaction> transactions;

    private String data;

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
    }

    /**
     * Creates a DataReply for sending over channels.
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

    public String getData() {
        return data;
    }

    public void setData(final String newData) {
        this.data = newData;
    }
}
