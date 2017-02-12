package ledger;

/**
 * @author Saul
 */
public class Transaction {

    private long transactionID;
    private long timestamp;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private double transactionAmount;
    private boolean processed;
    private boolean successful;

    public Transaction(final long newTransactionID, final String newSourceAccountNumber, final String newDestinationAccountNumber, final String newDestinationAccountHolderName, final double newTransactionAmount) {
        this.transactionID = newTransactionID;
        this.timestamp = System.currentTimeMillis();
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.transactionAmount = newTransactionAmount;
        this.processed = false;
        this.successful = false;
    }

    public Transaction(final long newTransactionID, final long newTimestamp, final String newSourceAccountNumber, final String newDestinationAccountNumber, final String newDestinationAccountHolderName, final double newTransactionAmount) {
        this.transactionID = newTransactionID;
        this.timestamp = newTimestamp;
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.transactionAmount = newTransactionAmount;
        this.processed = false;
        this.successful = false;
    }

    public long getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(final long newTransactionID) {
        this.transactionID = newTransactionID;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final long newTimestamp) {
        this.timestamp = newTimestamp;
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

    public double getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(final double newTransactionAmount) {
        this.transactionAmount = newTransactionAmount;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(final boolean newProcessed) {
        this.processed = newProcessed;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(final boolean newSuccessful) {
        this.successful = newSuccessful;
    }
}
