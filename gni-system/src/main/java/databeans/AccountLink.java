package databeans;

/**
 * @author Noel
 * @version 1
 */
public class AccountLink {
    private Long customerId;
    private Account account;
    private boolean successfull;

    public AccountLink(final Long newCustomerId) {
        this.customerId = newCustomerId;
        this.account = null;
        this.successfull = false;
    }

    public AccountLink(final Long newCustomerId, final Account newAccount, final boolean newSuccessfull) {
        this.customerId = newCustomerId;
        this.account = newAccount;
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

    public Account getAccount() {
        return account;
    }

    public void setAccount(final Account newAccount) {
        account = newAccount;
    }

    public boolean isSuccessfull() {
        return successfull;
    }

    public void setSuccessfull(final boolean newSuccessfull) {
        successfull = newSuccessfull;
    }
}
