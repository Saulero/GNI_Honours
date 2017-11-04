package main.model.methods;

public class OpenSavingsAccount {

    private String authToken;
    private String iBAN;

    public OpenSavingsAccount(String authToken, String iBAN) {
        this.authToken = authToken;
        this.iBAN = iBAN;
    }
}
