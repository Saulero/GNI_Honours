package main.model.methods;

public class SetTransferLimit {

    private String authToken;
    private String iBAN;
    private double transferLimit;

    public SetTransferLimit(String authToken, String iBAN, double transferLimit) {
        this.authToken = authToken;
        this.iBAN = iBAN;
        this.transferLimit = transferLimit;
    }
}
