package main.model.methods;

public class UnblockCard {

    private String authToken;
    private String iBAN;
    private String pinCard;

    public UnblockCard(String authToken, String iBAN, String pinCard) {
        this.authToken = authToken;
        this.iBAN = iBAN;
        this.pinCard = pinCard;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getiBAN() {
        return iBAN;
    }

    public void setiBAN(String iBAN) {
        this.iBAN = iBAN;
    }

    public String getPinCard() {
        return pinCard;
    }

    public void setPinCard(String pinCard) {
        this.pinCard = pinCard;
    }
}
