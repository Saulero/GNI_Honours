package databeans;

/**
 * @Author noel
 */
public class CloseAccountReply {
    private boolean customerRemoved;

    private boolean successfull;

    private String errorMessage;

    public boolean isCustomerRemoved() {
        return customerRemoved;
    }

    public void setCustomerRemoved(final boolean newRemovedCustomer) {
        customerRemoved = newRemovedCustomer;
    }

    public boolean isSuccessfull() {
        return successfull;
    }

    public void setSuccessfull(final boolean newSuccessfull) {
        successfull = newSuccessfull;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String newErrorMessage) {
        errorMessage = newErrorMessage;
    }
}
