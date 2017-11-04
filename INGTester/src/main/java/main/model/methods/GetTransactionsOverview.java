package main.model.methods;

public class GetTransactionsOverview {

    private String authToken;
    private String iBAN;
    private int nrOfTransactions;

    public GetTransactionsOverview(String authToken, String iBAN, int nrOfTransactions) {
        this.authToken = authToken;
        this.iBAN = iBAN;
        this.nrOfTransactions = nrOfTransactions;
    }
}
