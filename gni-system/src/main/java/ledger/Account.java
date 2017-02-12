package ledger;

/**
 * @author Saul
 */
public class Account {

    private String accountNumber;
    private String accountHolderName;
    private double spendingLimit;
    private double balance;

    public Account(final String newAccountHolderName, final double newSpendingLimit, final double newBalance) {
        this.accountHolderName = newAccountHolderName;
        this.spendingLimit = newSpendingLimit;
        this.balance = newBalance;
    }

    public boolean withdrawTransactionIsAllowed(final Transaction transaction) {
        return transaction.getTransactionAmount() <= spendingLimit;
    }

    public void processWithdraw(final Transaction transaction) {
        this.spendingLimit -= transaction.getTransactionAmount();
        this.balance -= transaction.getTransactionAmount();
    }

    public void processDeposit(final Transaction transaction) {
        this.spendingLimit += transaction.getTransactionAmount();
        this.balance += transaction.getTransactionAmount();
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
