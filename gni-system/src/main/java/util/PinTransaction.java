package util;

/**
 * Created by noel on 23-2-17.
 * @author noel
 * @version 1
 */
public class PinTransaction {
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private String pinCode;
    private String cardNumber;
    private double transactionAmount;

    public PinTransaction(final String newSourceAccountNumber, final String newDestinationAccountNumber,
                          final String newDestinationAccountHolderName, final String newPinCode,
                          final String newCardNumber, final double newTransactionAmount) {
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.pinCode = newPinCode;
        this.cardNumber = newCardNumber;
        this.transactionAmount = newTransactionAmount;
    }

    public PinTransaction(){ }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(String sourceAccountNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public void setDestinationAccountNumber(String destinationAccountNumber) {
        this.destinationAccountNumber = destinationAccountNumber;
    }

    public String getDestinationAccountHolderName() {
        return destinationAccountHolderName;
    }

    public void setDestinationAccountHolderName(String destinationAccountHolderName) {
        this.destinationAccountHolderName = destinationAccountHolderName;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public double getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(double transactionAmount) {
        this.transactionAmount = transactionAmount;
    }
}
