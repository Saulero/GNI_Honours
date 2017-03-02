package databeans;

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

    public PinTransaction(){
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(final String newSourceAccountNumber) {
        this.sourceAccountNumber = newSourceAccountNumber;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public void setDestinationAccountNumber(final String newDestinationAccountNumber) {
        this.destinationAccountNumber = newDestinationAccountNumber;
    }

    public String getDestinationAccountHolderName() {
        return destinationAccountHolderName;
    }

    public void setDestinationAccountHolderName(final String newDestinationAccountHolderName) {
        this.destinationAccountHolderName = newDestinationAccountHolderName;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(final String newPinCode) {
        this.pinCode = newPinCode;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(final String newCardNumber) {
        this.cardNumber = newCardNumber;
    }

    public double getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(final double newTransactionAmount) {
        this.transactionAmount = newTransactionAmount;
    }
}
