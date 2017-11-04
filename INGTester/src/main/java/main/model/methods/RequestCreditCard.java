package main.model.methods;

public class RequestCreditCard {

    private String authToken;
    private String iBAN;

    public RequestCreditCard(String authToken, String iBAN) {
        this.authToken = authToken;
        this.iBAN = iBAN;
    }
}
