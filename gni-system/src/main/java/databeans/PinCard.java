package databeans;

import java.util.Date;

/**
 * @author Noel
 */
public class PinCard {
    private String accountNumber;
    private Long cardNumber;
    private String pinCode;
    private Long customerId;
    private Date expirationDate;

    public PinCard(final String accountNumber, final Long cardNumber, final String pinCode, final Long customerId,
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

    public Long getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(final Long newCardNumber) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PinCard pinCard = (PinCard) o;

        if (accountNumber != null ? !accountNumber.equals(pinCard.accountNumber) : pinCard.accountNumber != null)
            return false;
        if (cardNumber != null ? !cardNumber.equals(pinCard.cardNumber) : pinCard.cardNumber != null) return false;
        if (pinCode != null ? !pinCode.equals(pinCard.pinCode) : pinCard.pinCode != null) return false;
        if (customerId != null ? !customerId.equals(pinCard.customerId) : pinCard.customerId != null) return false;
        return expirationDate != null ? expirationDate.equals(pinCard.expirationDate) : pinCard.expirationDate == null;
    }

    @Override
    public int hashCode() {
        int result = accountNumber != null ? accountNumber.hashCode() : 0;
        result = 31 * result + (cardNumber != null ? cardNumber.hashCode() : 0);
        result = 31 * result + (pinCode != null ? pinCode.hashCode() : 0);
        result = 31 * result + (customerId != null ? customerId.hashCode() : 0);
        result = 31 * result + (expirationDate != null ? expirationDate.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PinCard{" +
                "accountNumber='" + accountNumber + '\'' +
                ", cardNumber=" + cardNumber +
                ", pinCode='" + pinCode + '\'' +
                ", customerId=" + customerId +
                ", expirationDate=" + expirationDate +
                '}';
    }
}
