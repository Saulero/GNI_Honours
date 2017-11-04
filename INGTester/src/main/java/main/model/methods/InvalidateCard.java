package main.model.methods;

public class InvalidateCard {

    private String authToken;
    private String iBAN;
    private String pinCard;
    private boolean newPin;

    public InvalidateCard(String authToken, String iBAN, String pinCard, boolean newPin) {
        this.authToken = authToken;
        this.iBAN = iBAN;
        this.pinCard = pinCard;
        this.newPin = newPin;
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

    public boolean isNewPin() {
        return newPin;
    }

    public void setNewPin(boolean newPin) {
        this.newPin = newPin;
    }
}
