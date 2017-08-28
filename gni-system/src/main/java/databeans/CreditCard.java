package databeans;

import java.time.LocalDate;

/**
 * @Author noel
 */
public class CreditCard {
    private String accountNumber;
    private Long creditCardNumber;
    private String username;
    private Double limit;
    private Double balance;
    private Double fee;
    private LocalDate activationDate;
    private LocalDate expirationDate;
    private String pinCode;
    private Long incorrect_attempts;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        accountNumber = newAccountNumber;
    }

    public Long getCreditCardNumber() {
        return creditCardNumber;
    }

    public void setCreditCardNumber(final Long newCreditCardNumber) {
        creditCardNumber = newCreditCardNumber;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String newUsername) {
        username = newUsername;
    }

    public LocalDate getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(final LocalDate newActivationDate) {
        activationDate = newActivationDate;
    }

    public Double getLimit() {
        return limit;
    }

    public void setLimit(final Double newLimit) {
        limit = newLimit;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(final Double newBalance) {
        balance = newBalance;
    }

    public Double getFee() {
        return fee;
    }

    public void setFee(final Double newFee) {
        fee = newFee;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(final LocalDate newExpirationDate) {
        expirationDate = newExpirationDate;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(final String newPinCode) {
        pinCode = newPinCode;
    }

    public Long getIncorrect_attempts() {
        return incorrect_attempts;
    }

    public void setIncorrect_attempts(final Long newIncorrect_attempts) {
        incorrect_attempts = newIncorrect_attempts;
    }

    public void processTransaction(final PinTransaction pinTransaction) {
        this.balance -= pinTransaction.getTransactionAmount();
    }
}
