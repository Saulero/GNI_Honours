package databeans;

/**
 * @author Noel
 * @version 1
 */
public class AccountLink {
    private Long customerId;
    private String accountNumber;
    private boolean successfull;

    public AccountLink(final Long newCustomerId) {
        this.customerId = newCustomerId;
        this.accountNumber = null;
        this.successfull = false;
    }

    public AccountLink(final Long newCustomerId, final String newAccountNumber, final boolean newSuccessfull) {
        this.customerId = newCustomerId;
        this.accountNumber = newAccountNumber;
        this.successfull = newSuccessfull;
    }

    /** Constructor for Json conversions, do not use unless you manually fill the object afterwards. */
    public AccountLink() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final Long newCustomerId) {
        customerId = newCustomerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        accountNumber = newAccountNumber;
    }

    public boolean isSuccessfull() {
        return successfull;
    }

    public void setSuccessfull(final boolean newSuccessfull) {
        successfull = newSuccessfull;
    }
}
