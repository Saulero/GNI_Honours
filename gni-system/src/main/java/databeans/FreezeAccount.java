package databeans;

/**
 * @author Saul
 */
public class FreezeAccount {

    private boolean freeze;
    private String username;

    public FreezeAccount(boolean freeze, String username) {
        this.freeze = freeze;
        this.username = username;
    }

    public boolean isFreeze() {
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

    @Override
    public String toString() {
        return "FreezeAccount{" +
                "freeze=" + freeze +
                ", username='" + username + '\'' +
                '}';
    }
}
