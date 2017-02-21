package util;

import java.io.Serializable;

/**
 * @author Noel
 * @version 1
 * Databean used to send a request.
 */
//TODO needs to be reworked to not be dependent on accountNumber
public final class DataRequest implements Serializable {
    /** Account number the request is for. */
    private String accountNumber;
    /** Type of request {@link RequestType}. */
    private RequestType type;

    /** Creates a DataRequest objects which is used to send a request to a micro service.
     * @param newAccountNumber Account number relating to the request.
     * @param newType Type of data to request {@link RequestType}
     */
    public DataRequest(final String newAccountNumber, final RequestType newType) {
        this.accountNumber = newAccountNumber;
        this.type = newType;
    }

    /**
     * Empty constructor for json conversion of the object. Do not use as manual constructor.
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
}
