package databeans;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * @author Noel & Saul
 */
public class PinCard implements Serializable {
    private String accountNumber;
    private Long cardNumber;
    private String pinCode;
    private Long customerId;
    private LocalDate expirationDate;
    private boolean active;

    public PinCard(final String accountNumber, final Long cardNumber, final String pinCode, final Long customerId,
                   final LocalDate expirationDate, final boolean active) {
        this.accountNumber = accountNumber;
        this.cardNumber = cardNumber;
        this.pinCode = pinCode;
        this.customerId = customerId;
        this.expirationDate = expirationDate;
        this.active = active;
    }

    public PinCard(final String accountNumber, final Long cardNumber) {
        this.accountNumber = accountNumber;
        this.cardNumber = cardNumber;
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

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(final LocalDate newExpirationDate) {
        expirationDate = newExpirationDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PinCard pinCard = (PinCard) o;

        if (active != pinCard.active) return false;
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
        result = 31 * result + (active ? 1 : 0);
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
                ", active=" + active +
                '}';
    }
}
