package ledger;

/**
 * @author Saul
 */
public class Transaction2 {

    private long transactionID;
    private long timestamp;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private double transactionAmount;
    private boolean processed;
    private boolean successful;

    public Transaction2(final long newTransactionID, final String newSourceAccountNumber, final String newDestinationAccountNumber, final String newDestinationAccountHolderName, final double newTransactionAmount) {
        this.transactionID = newTransactionID;
        this.timestamp = -1;
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = newDestinationAccountHolderName;
        this.transactionAmount = newTransactionAmount;
        this.processed = false;
        this.successful = false;
    }

    public Transaction2(final long newTransactionID, final long newTimestamp, final String newSourceAccountNumber, final String newDestinationAccountNumber, final double newTransactionAmount) {
        this.transactionID = newTransactionID;
        this.timestamp = newTimestamp;
        this.sourceAccountNumber = newSourceAccountNumber;
        this.destinationAccountNumber = newDestinationAccountNumber;
        this.destinationAccountHolderName = "";
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

    public void generateTimestamp() {
        this.timestamp = System.currentTimeMillis();
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

    public boolean minimalEquals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction2)) return false;

        Transaction2 that = (Transaction2) o;

        if (getTransactionID() != that.getTransactionID()) return false;
        if (getTimestamp() != that.getTimestamp()) return false;
        if (Double.compare(that.getTransactionAmount(), getTransactionAmount()) != 0) return false;
        if (getSourceAccountNumber() != null ? !getSourceAccountNumber().equals(that.getSourceAccountNumber()) : that.getSourceAccountNumber() != null)
            return false;
        if (getDestinationAccountNumber() != null ? !getDestinationAccountNumber().equals(that.getDestinationAccountNumber()) : that.getDestinationAccountNumber() != null)
            return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction2)) return false;

        Transaction2 that = (Transaction2) o;

        if (getTransactionID() != that.getTransactionID()) return false;
        if (getTimestamp() != that.getTimestamp()) return false;
        if (Double.compare(that.getTransactionAmount(), getTransactionAmount()) != 0) return false;
        if (isProcessed() != that.isProcessed()) return false;
        if (isSuccessful() != that.isSuccessful()) return false;
        if (getSourceAccountNumber() != null ? !getSourceAccountNumber().equals(that.getSourceAccountNumber()) : that.getSourceAccountNumber() != null)
            return false;
        if (getDestinationAccountNumber() != null ? !getDestinationAccountNumber().equals(that.getDestinationAccountNumber()) : that.getDestinationAccountNumber() != null)
            return false;
        return getDestinationAccountHolderName() != null ? getDestinationAccountHolderName().equals(that.getDestinationAccountHolderName()) : that.getDestinationAccountHolderName() == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (getTransactionID() ^ (getTransactionID() >>> 32));
        result = 31 * result + (int) (getTimestamp() ^ (getTimestamp() >>> 32));
        result = 31 * result + (getSourceAccountNumber() != null ? getSourceAccountNumber().hashCode() : 0);
        result = 31 * result + (getDestinationAccountNumber() != null ? getDestinationAccountNumber().hashCode() : 0);
        result = 31 * result + (getDestinationAccountHolderName() != null ? getDestinationAccountHolderName().hashCode() : 0);
        temp = Double.doubleToLongBits(getTransactionAmount());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (isProcessed() ? 1 : 0);
        result = 31 * result + (isSuccessful() ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionID=" + transactionID +
                ", timestamp=" + timestamp +
                ", sourceAccountNumber='" + sourceAccountNumber + '\'' +
                ", destinationAccountNumber='" + destinationAccountNumber + '\'' +
                ", destinationAccountHolderName='" + destinationAccountHolderName + '\'' +
                ", transactionAmount=" + transactionAmount +
                ", processed=" + processed +
                ", successful=" + successful +
                '}';
    }
}
