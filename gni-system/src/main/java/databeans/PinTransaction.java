package databeans;

/**
 * @author Noel
 * @version 1
 */
public class PinTransaction {

    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private String pinCode;
    private Long cardNumber;
    private double transactionAmount;
    private boolean isATMTransaction;

    public PinTransaction(final String newSourceAccountNumber, final String newDestinationAccountNumber,
                          final String newDestinationAccountHolderName, final String newPinCode,
                          final Long newCardNumber, final double newTransactionAmount, final boolean isATMTransaction) {
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.pinCode = newPinCode;
        this.cardNumber = newCardNumber;
        this.transactionAmount = newTransactionAmount;
        this.isATMTransaction = isATMTransaction;
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

    public Long getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(final Long newCardNumber) {
        this.cardNumber = newCardNumber;
    }

    public double getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(final double newTransactionAmount) {
        this.transactionAmount = newTransactionAmount;
    }

    public boolean isATMTransaction() {
        return isATMTransaction;
    }

    public void setATMTransaction(final boolean newIsATMTransaction) {
        this.isATMTransaction = newIsATMTransaction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PinTransaction that = (PinTransaction) o;

        if (Double.compare(that.transactionAmount, transactionAmount) != 0) return false;
        if (isATMTransaction != that.isATMTransaction) return false;
        if (sourceAccountNumber != null ? !sourceAccountNumber.equals(that.sourceAccountNumber) : that.sourceAccountNumber != null)
            return false;
        if (destinationAccountNumber != null ? !destinationAccountNumber.equals(that.destinationAccountNumber) : that.destinationAccountNumber != null)
            return false;
        if (destinationAccountHolderName != null ? !destinationAccountHolderName.equals(that.destinationAccountHolderName) : that.destinationAccountHolderName != null)
            return false;
        if (pinCode != null ? !pinCode.equals(that.pinCode) : that.pinCode != null) return false;
        return cardNumber != null ? cardNumber.equals(that.cardNumber) : that.cardNumber == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = sourceAccountNumber != null ? sourceAccountNumber.hashCode() : 0;
        result = 31 * result + (destinationAccountNumber != null ? destinationAccountNumber.hashCode() : 0);
        result = 31 * result + (destinationAccountHolderName != null ? destinationAccountHolderName.hashCode() : 0);
        result = 31 * result + (pinCode != null ? pinCode.hashCode() : 0);
        result = 31 * result + (cardNumber != null ? cardNumber.hashCode() : 0);
        temp = Double.doubleToLongBits(transactionAmount);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (isATMTransaction ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PinTransaction{" +
                "sourceAccountNumber='" + sourceAccountNumber + '\'' +
                ", destinationAccountNumber='" + destinationAccountNumber + '\'' +
                ", destinationAccountHolderName='" + destinationAccountHolderName + '\'' +
                ", pinCode='" + pinCode + '\'' +
                ", cardNumber=" + cardNumber +
                ", transactionAmount=" + transactionAmount +
                ", isATMTransaction=" + isATMTransaction +
                '}';
    }
}
