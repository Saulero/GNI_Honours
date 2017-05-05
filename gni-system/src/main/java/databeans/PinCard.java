package databeans;

import java.util.Date;

/**
 * @Author noel
 */
public class PinCard {
    private String accountNumber;
    private String cardNumber;
    private String pinCode;
    private Long customerId;
    private Date expirationDate;

    public PinCard(final String accountNumber, final String cardNumber, final String pinCode, final Long customerId,
                   final Date expirationDate) {
        this.accountNumber = accountNumber;
        this.cardNumber = cardNumber;
        this.pinCode = pinCode;
        this.customerId = customerId;
        this.expirationDate = expirationDate;
    }

    public PinCard() {
    }

    public String getAccountNumber() { return accountNumber; }

    public void setAccountNumber(final String newAccountNumber) {
        accountNumber = newAccountNumber;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(final String newCardNumber) {
        cardNumber = newCardNumber;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(final String newPinCode) {
        pinCode = newPinCode;
    }

    public Long getCustomerId() { return customerId; }

    public void setCustomerId(final Long newCustomerId) {
        customerId = newCustomerId;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(final Date newExpirationDate) {
        expirationDate = newExpirationDate;
    }
}
