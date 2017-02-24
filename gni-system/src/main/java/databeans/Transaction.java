package databeans;

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

    public Transaction(final long transactionID, final String sourceAccountNumber,
                       final String destinationAccountNumber, final String destinationAccountHolderName,
                       final double transactionAmount) {
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

    /**
     * Compares all variables of transaction to the current object and checks if they are equal.
     * @param transaction object to check for equality.
     * @return boolean indicating if the objects are equal.
     */
    public boolean equals(final Transaction transaction) {
        return this.getTransactionID() == transaction.getTransactionID() && this.getSourceAccountNumber().equals(
                transaction.getSourceAccountNumber()) && this.getDestinationAccountNumber().equals(
                transaction.getDestinationAccountNumber()) && this.getDestinationAccountHolderName()
        .equals(transaction.getDestinationAccountHolderName()) && this.getTransactionAmount()
                == transaction.getTransactionAmount() && this.isProcessed() == transaction.isProcessed()
                && this.isSuccessfull() == transaction.isSuccessfull();
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
                && this.getTransactionAmount() == transaction.getTransactionAmount();
    }

    /**
     * Checks if the Transaction has a GNIB source accountNumber.
     * @return True if the sourceAccountNumber is a GNIB accountNumber, otherwise returns false.
     */
    public boolean isGNIBSource() {
        return this.getSourceAccountNumber().contains("GNIB");
    }

    /**
     * Checks if the Transaction has a GNIB destination accountNumber.
     * @return True if the destinationAccountNumber is a GNIB accountNumber, otherwise returns false.
     */
    public boolean isGNIBDestination() {
        return this.getDestinationAccountNumber().contains("GNIB");
    }
}
