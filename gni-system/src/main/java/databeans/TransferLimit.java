package databeans;

/**
 * @author Noel
 */
public class TransferLimit {
    private String iBAN;
    private Double transferLimit;

    public TransferLimit(final String newIBAN, final Double newTransferLimit) {
        this.iBAN = newIBAN;
        this.transferLimit = newTransferLimit;
    }

    public String getIBAN() {
        return iBAN;
    }

    public void setIBAN(final String newIBAN) {
        iBAN = newIBAN;
    }

    public Double getTransferLimit() {
        return transferLimit;
    }

    public void setTransferLimit(final Double newTransferLimit) {
        transferLimit = newTransferLimit;
    }
}
