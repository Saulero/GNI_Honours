package databeans;

import java.io.Serializable;

/**
 * @author Noel
 * @version 1
 * Databean used to send a request.
 */
public final class DataRequest implements Serializable {
    /** Account number the request is for. */
    private String accountNumber;
    /** Type of request {@link RequestType}. */
    private RequestType type;
    /** User id of the customer the request is for. */
    private long customerId;

    /** Creates a DataRequest objects which is used to send a request to the Ledger service.
     * @param newAccountNumber Account number relating to the request.
     * @param newType Type of data to request {@link RequestType}
     * @param newCustomerId Identifier of the customer the request is for.
     */
    public DataRequest(final String newAccountNumber, final RequestType newType, final long newCustomerId) {
        this.accountNumber = newAccountNumber;
        this.type = newType;
        this.customerId = newCustomerId;
    }

    /**
     * Empty constructor for Json conversion of the object. Do not use as manual constructor.
     */
    public DataRequest() { }

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

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final Long newCustomerId) {
        this.customerId = newCustomerId;
    }
}
