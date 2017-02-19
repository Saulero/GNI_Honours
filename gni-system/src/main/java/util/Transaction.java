package util;

/**
 * @author Saul
 */
public class Transaction {

    private long transactionID;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private double transactionAmount;
    private boolean processed;
    private boolean successfull;

    public Transaction(long transactionID, String sourceAccountNumber, String destinationAccountNumber, String destinationAccountHolderName, double transactionAmount) {
        this.transactionID = transactionID;
        this.sourceAccountNumber = sourceAccountNumber;
        this.destinationAccountNumber = destinationAccountNumber;
        this.destinationAccountHolderName = destinationAccountHolderName;
        this.transactionAmount = transactionAmount;
        this.processed = false;
        this.successfull = false;
    }

    /**
     * Empty constructor for json conversion of the object. Do not use as manual constructor.
     */
    public Transaction() { }

    public long getTransactionID() {
        return transactionID;
    }

    public void setTransactionID(long transactionID) {
        this.transactionID = transactionID;
    }

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

    public double getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(double transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public boolean isSuccessfull() {
        return successfull;
    }

    public void setSuccessfull(boolean successfull) {
        this.successfull = successfull;
    }
}

