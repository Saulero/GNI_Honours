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
    private Long userId;

    /** Creates a DataRequest objects which is used to send a request to the Ledger service.
     * @param newAccountNumber Account number relating to the request.
     * @param newType Type of data to request {@link RequestType}
     */
    public DataRequest(final String newAccountNumber, final RequestType newType, final Long newUserId) {
        this.accountNumber = newAccountNumber;
        this.type = newType;
        this.userId = newUserId;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(final Long newUserId) {
        this.userId = newUserId;
    }
}
