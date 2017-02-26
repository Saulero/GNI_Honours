package ledger;

import java.io.Serializable;

/**
 * @author Saul
 */
public class Account implements Serializable{

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;

        Account account = (Account) o;

        if (Double.compare(account.getSpendingLimit(), getSpendingLimit()) != 0) return false;
        if (Double.compare(account.getBalance(), getBalance()) != 0) return false;
        if (getAccountNumber() != null ? !getAccountNumber().equals(account.getAccountNumber()) : account.getAccountNumber() != null)
            return false;
        return getAccountHolderName() != null ? getAccountHolderName().equals(account.getAccountHolderName()) : account.getAccountHolderName() == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = getAccountNumber() != null ? getAccountNumber().hashCode() : 0;
        result = 31 * result + (getAccountHolderName() != null ? getAccountHolderName().hashCode() : 0);
        temp = Double.doubleToLongBits(getSpendingLimit());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getBalance());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
