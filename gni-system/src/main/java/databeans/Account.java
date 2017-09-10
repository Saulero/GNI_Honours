package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public class Account implements Serializable {

    private String accountNumber;
    private String accountHolderName;
    private double overdraftLimit;
    private double balance;
    private boolean savingsActive;
    private double savingsBalance;
    private Double creditCardBalance;
    private Double transferLimit;

    public Account(final String newAccountHolderName, final double newOverdraftLimit, final double newBalance,
                   final boolean savingsActive, final double savingsBalance) {
        this.accountHolderName = newAccountHolderName;
        this.overdraftLimit = newOverdraftLimit;
        this.balance = newBalance;
        this.savingsActive = savingsActive;
        this.savingsBalance = savingsBalance;
    }

    public Account(final String newAccountHolderName, final double newOverdraftLimit, final double newBalance,
                   final boolean savingsActive, final double savingsBalance, final double creditCardBalance) {
        this.accountHolderName = newAccountHolderName;
        this.overdraftLimit = newOverdraftLimit;
        this.balance = newBalance;
        this.savingsActive = savingsActive;
        this.savingsBalance = savingsBalance;
        this.creditCardBalance = creditCardBalance;
    }

    public Account(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    /** Used for Json conversions, only use if you manually fill the object afterwards. */
    public Account() {

    }

    public boolean withdrawTransactionIsAllowed(final Transaction transaction) {
        if (transaction.getDestinationAccountNumber().equals(transaction.getSourceAccountNumber() + "S")) {
            return savingsActive && transaction.getTransactionAmount() <= (balance + overdraftLimit);
        } else if (transaction.getSourceAccountNumber().equals(transaction.getDestinationAccountNumber() + "S")) {
            return savingsActive && transaction.getTransactionAmount() <= savingsBalance;
        } else {
            return transaction.getTransactionAmount() <= (balance + overdraftLimit);
        }
    }

    public void processWithdraw(final Transaction transaction) {
        if ((transaction.getSourceAccountNumber().equals(transaction.getDestinationAccountNumber() + "S"))) {
            // transfer from savings to normal account
            this.balance += transaction.getTransactionAmount();
            this.savingsBalance -= transaction.getTransactionAmount();
        } else if ((transaction.getDestinationAccountNumber()).equals(transaction.getSourceAccountNumber() + "S")) {
            // deposit comes from savings account, update savings balance
            this.savingsBalance += transaction.getTransactionAmount();
            this.balance -= transaction.getTransactionAmount();
        } else {
            // normal withdraw
            this.balance -= transaction.getTransactionAmount();
        }

    }

    public void processDeposit(final Transaction transaction) {
        this.balance += transaction.getTransactionAmount();
        if ((transaction.getSourceAccountNumber()).equals(transaction.getDestinationAccountNumber() + "S")) {
            // deposit comes from savings account, update savings balance
            this.savingsBalance -= transaction.getTransactionAmount();
        }
    }

    public void processSavingsInterest(final Transaction transaction) {
        this.savingsBalance += transaction.getTransactionAmount();
    }

    public void transferSavingsToMain() {
        this.balance += this.savingsBalance;
        this.savingsBalance = 0;
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

    public double getOverdraftLimit() {
        return overdraftLimit;
    }

    public void setOverdraftLimit(final double newOverdraftLimit) {
        this.overdraftLimit = newOverdraftLimit;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(final double newBalance) {
        this.balance = newBalance;
    }


    public boolean isSavingsActive() {
        return savingsActive;
    }

    public void setSavingsActive(final boolean newSavingsActive) {
        savingsActive = newSavingsActive;
    }

    public double getSavingsBalance() {
        return savingsBalance;
    }

    public void setSavingsBalance(final Double newSavingsBalance) {
        savingsBalance = newSavingsBalance;
    }

    public Double getCreditCardBalance() {
        return creditCardBalance;
    }

    public void setCreditCardBalance(final double newCreditCardBalance) {
        creditCardBalance = newCreditCardBalance;
    }

    public void setSavingsBalance(final double newSavingsBalance) {
        savingsBalance = newSavingsBalance;
    }

    public void setCreditCardBalance(final Double newCreditCardBalance) {
        creditCardBalance = newCreditCardBalance;
    }

    public Double getTransferLimit() {
        return transferLimit;
    }

    public void setTransferLimit(final Double newTransferLimit) {
        transferLimit = newTransferLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;

        Account account = (Account) o;

        if (Double.compare(account.getOverdraftLimit(), getOverdraftLimit()) != 0) return false;
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
        temp = Double.doubleToLongBits(getOverdraftLimit());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getBalance());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountNumber='" + accountNumber + '\'' +
                ", accountHolderName='" + accountHolderName + '\'' +
                ", overdraftLimit=" + overdraftLimit +
                ", balance=" + balance +
                '}';
    }
}
