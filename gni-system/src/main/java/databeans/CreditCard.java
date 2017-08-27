package databeans;

import java.time.LocalDate;

/**
 * @Author noel
 */
public class CreditCard {
    private String accountNumber;
    private String creditCardNumber;
    private String cardHolderName;
    private String username;
    private Double limit;
    private Double balance;
    private Double fee;
    private LocalDate activationDate;
    private PinCard pinCard;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        accountNumber = newAccountNumber;
    }

    public String getCreditCardNumber() {
        return creditCardNumber;
    }

    public void setCreditCardNumber(final String newCreditCardNumber) {
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

    public PinCard getPinCard() {
        return pinCard;
    }

    public void setPinCard(final PinCard newPinCard) {
        pinCard = newPinCard;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(final String newCardHolderName) {
        cardHolderName = newCardHolderName;
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
}
