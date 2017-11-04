package main.model.methods;

public class SetFreezeUserAccount {

    private String authToken;
    private String username;
    private boolean freeze;

    public SetFreezeUserAccount(String authToken, String username, boolean freeze) {
        this.authToken = authToken;
        this.username = username;
        this.freeze = freeze;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isFreeze() {
        return freeze;
    }

    public void setFreeze(boolean freeze) {
        this.freeze = freeze;
    }
}
