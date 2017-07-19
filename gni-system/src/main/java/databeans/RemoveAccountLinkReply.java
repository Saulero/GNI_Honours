package databeans;

/**
 * @Author noel
 */
public class RemoveAccountLinkReply {
    private boolean successfull;
    private String errorMessage;

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
