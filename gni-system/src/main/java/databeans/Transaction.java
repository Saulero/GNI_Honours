package databeans;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author Saul
 */
public class Transaction implements Serializable {

    private long transactionID;
    private LocalDate date;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private String description;
    private double transactionAmount;
    private double newBalance;
    private double newSavingsBalance;
    private boolean processed;
    private boolean successful;

    public Transaction(final long newTransactionID, final String newSourceAccountNumber,
                       final String newDestinationAccountNumber, final String newDestinationAccountHolderName,
                       final String newDescription, final double newTransactionAmount) {
        this.transactionID = newTransactionID;
        this.date = null;
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.description = newDescription;
        this.transactionAmount = newTransactionAmount;
        this.processed = false;
        this.successful = false;
    }

    public Transaction(final long newTransactionID, final LocalDate newDate, final String newSourceAccountNumber,
                       final String newDestinationAccountNumber, final String newDestinationAccountHolderName,
                       final String newDescription, final double newTransactionAmount, final double newNewBalance) {
        this.transactionID = newTransactionID;
        this.date = newDate;
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.description = newDescription;
        this.transactionAmount = newTransactionAmount;
        this.newBalance = newNewBalance;
        this.processed = false;
        this.successful = false;
    }

    public Transaction() { }

    public long getTransactionID() {
        return this.transactionID;
    }

    public void setTransactionID(final long newTransactionID) {
        this.transactionID = newTransactionID;
    }

    public String getSourceAccountNumber() {
        return this.sourceAccountNumber;
    }

    public void setSourceAccountNumber(final String newSourceAccountNumber) {
        this.sourceAccountNumber = newSourceAccountNumber;
    }

    public String getDestinationAccountNumber() {
        return this.destinationAccountNumber;
    }

    public void setDestinationAccountNumber(final String newDestinationAccountNumber) {
        this.destinationAccountNumber = newDestinationAccountNumber;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDestinationAccountHolderName() {
        return this.destinationAccountHolderName;
    }

    public void setDestinationAccountHolderName(final String newDestinationAccountHolderName) {
        this.destinationAccountHolderName = newDestinationAccountHolderName;
    }

    public double getTransactionAmount() {
        return this.transactionAmount;
    }

    public void setTransactionAmount(final double newTransactionAmount) {
        this.transactionAmount = newTransactionAmount;
    }

    public double getNewBalance() {
        return newBalance;
    }

    public void setNewBalance(final double newNewBalance) {
        this.newBalance = newNewBalance;
    }

    public boolean isProcessed() {
        return this.processed;
    }

    public void setProcessed(final boolean newProcessed) {
        this.processed = newProcessed;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public void setSuccessful(final boolean newSuccessful) {
        this.successful = newSuccessful;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String newDescription) {
        this.description = newDescription;
    }

    public double getNewSavingsBalance() {
        return newSavingsBalance;
    }

    public void setNewSavingsBalance(final double newNewSavingsBalance) {
        newSavingsBalance = newNewSavingsBalance;
    }

    /**
     * Used to compare a transaction request where the transactionID is not set to the reply where it is set.
     * @param transaction Transaction request.
     * @return If the request is equal to the reply on every variable except transactionID.
     */
    public boolean equalsRequest(final Transaction transaction) {
        return this.getSourceAccountNumber().equals(transaction.getSourceAccountNumber())
                && this.getDestinationAccountNumber().equals(transaction.getDestinationAccountNumber())
                && this.getDestinationAccountHolderName().equals(transaction.getDestinationAccountHolderName())
                && this.getDescription().equals(transaction.getDescription())
                && this.getTransactionAmount() == transaction.getTransactionAmount()
                && this.getNewBalance() == transaction.getNewBalance();
    }

    public boolean minimalEquals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;

        Transaction that = (Transaction) o;

        if (getTransactionID() != that.getTransactionID()) return false;
        if (!getDate().equals(that.getDate())) return false;
        if (Double.compare(that.getTransactionAmount(), getTransactionAmount()) != 0) return false;
        if (Double.compare(that.getNewBalance(), getNewBalance()) != 0) return false;
        if (getSourceAccountNumber() != null ? !getSourceAccountNumber().equals(that.getSourceAccountNumber()) : that.getSourceAccountNumber() != null)
            return false;
        if (getDestinationAccountNumber() != null ? !getDestinationAccountNumber().equals(that.getDestinationAccountNumber()) : that.getDestinationAccountNumber() != null)
            return false;
        return true;
    }
}
