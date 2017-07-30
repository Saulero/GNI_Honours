package databeans;

/**
 * @Author noel
 */
public class CloseAccountReply {
    private boolean customerRemoved;

    private boolean successful;

    private String errorMessage;

    public boolean isCustomerRemoved() {
        return customerRemoved;
    }

    public void setCustomerRemoved(final boolean newRemovedCustomer) {
        customerRemoved = newRemovedCustomer;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(final boolean newSuccessful) {
        successful = newSuccessful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String newErrorMessage) {
        errorMessage = newErrorMessage;
    }

    @Override
    public String toString() {
        return "CloseAccountReply{" +
                "customerRemoved=" + customerRemoved +
                ", successful=" + successful +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
