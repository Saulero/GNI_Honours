package main.model.methods;

public class GetBalance {

    private String authToken;
    private String iBAN;

    public GetBalance(String authToken, String iBAN) {
        this.authToken = authToken;
        this.iBAN = iBAN;
    }
}
