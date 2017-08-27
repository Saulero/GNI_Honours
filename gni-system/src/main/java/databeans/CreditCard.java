package databeans;

/**
 * @Author noel
 */
public class CreditCard {
    private String accountNumber;
    private String creditCardNumber;
    private String username;
    private Long customerId;
    private String activationDate;
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final Long newCustomerId) {
        customerId = newCustomerId;
    }

    public String getActivationDate() {
        return activationDate;
    }

    public void setActivationDate(final String newActivationDate) {
        activationDate = newActivationDate;
    }

    public PinCard getPinCard() {
        return pinCard;
    }

    public void setPinCard(final PinCard newPinCard) {
        pinCard = newPinCard;
    }
}
