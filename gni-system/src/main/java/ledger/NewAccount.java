package ledger;

/**
 * @author Saul
 */
public class NewAccount {

    private String accountNumber;
    private String accountHolderName;
    private double spendingLimit;
    private double balance;

    public NewAccount(final String newAccountHolderName, final double newSpendingLimit, final double newBalance) {
        this.accountHolderName = newAccountHolderName;
        this.spendingLimit = newSpendingLimit;
        this.balance = newBalance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        this.accountNumber = newAccountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(final String newAccountHolderName) {
        this.accountHolderName = newAccountHolderName;
    }

    public double getSpendingLimit() {
        return spendingLimit;
    }

    public void setSpendingLimit(final double newSpendingLimit) {
        this.spendingLimit = newSpendingLimit;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(final double newBalance) {
        this.balance = newBalance;
    }
}
