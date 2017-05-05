package databeans;

/**
 * @Author noel
 */
public class PinCard {
    private String cardNumber;
    private String pinCode;

    public PinCard(final String cardNumber, final String pinCode) {
        this.cardNumber = cardNumber;
        this.pinCode = pinCode;
    }

    public PinCard() {
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
}
