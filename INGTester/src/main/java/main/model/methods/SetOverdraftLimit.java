package main.model.methods;

public class SetOverdraftLimit {

    private String authToken;
    private String iBAN;
    private double overdraftLimit;

    public SetOverdraftLimit(String authToken, String iBAN, double overdraftLimit) {
        this.authToken = authToken;
        this.iBAN = iBAN;
        this.overdraftLimit = overdraftLimit;
    }
}
