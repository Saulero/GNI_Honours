package main.model.methods;

public class DepositIntoAccount {

    private String iBAN;
    private String pinCard;
    private String pinCode;
    private double amount;

    public DepositIntoAccount(String iBAN, String pinCard, String pinCode, double amount) {
        this.iBAN = iBAN;
        this.pinCard = pinCard;
        this.pinCode = pinCode;
        this.amount = amount;
    }
}
