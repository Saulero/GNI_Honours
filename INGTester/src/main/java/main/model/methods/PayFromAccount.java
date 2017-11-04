package main.model.methods;

public class PayFromAccount {

    private String sourceIBAN;
    private String targetIBAN;
    private String pinCard;
    private String pinCode;
    private double amount;

    public PayFromAccount(String sourceIBAN, String targetIBAN, String pinCard, String pinCode, double amount) {
        this.sourceIBAN = sourceIBAN;
        this.targetIBAN = targetIBAN;
        this.pinCard = pinCard;
        this.pinCode = pinCode;
        this.amount = amount;
    }

    public String getSourceIBAN() {
        return sourceIBAN;
    }

    public void setSourceIBAN(String sourceIBAN) {
        this.sourceIBAN = sourceIBAN;
    }

    public String getTargetIBAN() {
        return targetIBAN;
    }

    public void setTargetIBAN(String targetIBAN) {
        this.targetIBAN = targetIBAN;
    }

    public String getPinCard() {
        return pinCard;
    }

    public void setPinCard(String pinCard) {
        this.pinCard = pinCard;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
