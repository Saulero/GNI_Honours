package databeans;

/**
 * @author Noel
 * @version 1
 */
public class AccountLink {
    private Long customerId;
    private String username;
    private String accountNumber;
    private boolean successful;

    public AccountLink(final Long newCustomerId) {
        this.customerId = newCustomerId;
        this.accountNumber = null;
        this.successful = false;
    }

    public AccountLink(final Long newCustomerId, final String newAccountNumber, final boolean newSuccessfull) {
        this.customerId = newCustomerId;
        this.accountNumber = newAccountNumber;
        this.successful = newSuccessfull;
    }

    public AccountLink(final String newUsername, final String newAccountNumber, final boolean newSuccessfull) {
        this.username = newUsername;
        this.accountNumber = newAccountNumber;
        this.successful = newSuccessfull;
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

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(final boolean newSuccessful) {
        successful = newSuccessful;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String newUsername) {
        username = newUsername;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountLink that = (AccountLink) o;

        if (successful != that.successful) return false;
        if (customerId != null ? !customerId.equals(that.customerId) : that.customerId != null) return false;
        return accountNumber != null ? accountNumber.equals(that.accountNumber) : that.accountNumber == null;
    }

    @Override
    public int hashCode() {
        int result = customerId != null ? customerId.hashCode() : 0;
        result = 31 * result + (accountNumber != null ? accountNumber.hashCode() : 0);
        result = 31 * result + (successful ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AccountLink{" +
                "customerId=" + customerId +
                ", accountNumber='" + accountNumber + '\'' +
                ", successful=" + successful +
                '}';
    }
}
