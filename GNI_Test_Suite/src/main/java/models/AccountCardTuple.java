package models;

public class AccountCardTuple {
    private BankAccount account;
    private PinCard card;

    public AccountCardTuple(BankAccount account, PinCard card) {
        this.card = card;
        this.account = account;
    }

    public BankAccount getAccount() {
        return account;
    }

    public PinCard getCard() {
        return card;
    }
}
