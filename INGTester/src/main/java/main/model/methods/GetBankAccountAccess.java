package main.model.methods;

public class GetBankAccountAccess {

    private String authToken;
    private String iBAN;

    public GetBankAccountAccess(String authToken, String iBAN) {
        this.authToken = authToken;
        this.iBAN = iBAN;
    }
}
