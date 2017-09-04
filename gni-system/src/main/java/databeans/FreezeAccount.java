package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public class FreezeAccount implements Serializable {

    private boolean freeze;
    private String username;
    private long customerId;

    public FreezeAccount(boolean freeze, String username) {
        this.freeze = freeze;
        this.username = username;
    }

    public boolean getFreeze() {
        return freeze;
    }

    public void setFreeze(boolean freeze) {
        this.freeze = freeze;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    @Override
    public String toString() {
        return "FreezeAccount{" +
                "freeze=" + freeze +
                ", username='" + username + '\'' +
                ", customerId=" + customerId +
                '}';
    }
}
