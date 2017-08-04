package databeans;

import java.io.Serializable;

/**
 * @author Noel & Saul
 */
public class RemoveAccountLinkReply implements Serializable {

    private boolean successful;
    private String message;

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
