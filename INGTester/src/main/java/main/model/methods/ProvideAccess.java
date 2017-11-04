package main.model.methods;

public class ProvideAccess {

    private String authToken;
    private String iBAN;
    private String username;

    public ProvideAccess(String authToken, String iBAN, String username) {
        this.authToken = authToken;
        this.iBAN = iBAN;
        this.username = username;
    }
}
