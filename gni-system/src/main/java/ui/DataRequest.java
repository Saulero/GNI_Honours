package ui;

/**
 * @author Noel
 * @version 1
 * Databean used to send a request over a Qbit queue.
 */
public final class DataRequest {
    /** Account number the request is for. */
    private String accountNumber;
    /** Type of request {@link RequestType}. */
    private RequestType type;

    /** Creates a DataRequest objects which is used to send a request to a
     * micro service over a Qbit queue.
     * @param newAccountNumber Account number relating to the request.
     * @param newType Type of data to request {@link RequestType}
     */
    public DataRequest(final String newAccountNumber, final RequestType newType) {
        this.accountNumber = newAccountNumber;
        this.type = newType;
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
}
