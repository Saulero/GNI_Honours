package ui;

/**
 * @author Noel
 * @version 2
 * Databean used to send a request over a Qbit queue.
 */
public final class DataReply {
    /** Account number the reply corresponds to */
    private String accountNumber;
    /** The type of request that this reply is for */
    private RequestType type;
    /** The data of the reply, for use of other classes to process the reply */
    private String data;

    /**
     * Creates a DataReply for sending over channels.
     * @param newAccountNumber Account number the reply corresponds to
     * @param newType Type of request that this reply is for
     * @param newData Data of the reply
     */
    public DataReply(final String newAccountNumber, final RequestType newType, final String newData) {
        this.accountNumber = accountNumber;
        this.type = type;
        this.data = data;
    }

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

    public String getData() {
        return data;
    }

    public void setData(final String newData) {
        this.data = newData;
    }
}
