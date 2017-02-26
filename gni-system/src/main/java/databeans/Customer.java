package databeans;

import ledger.Account;

import java.io.Serializable;

/**
 * @author Noel
 * @version 1
 * Databean used to send customer data over queues.
 */
public final class Customer implements Serializable {
    /** First name of the customer. */
    private String name;
    /** Surname of the customer. */
    private String surname;
    /** Account of the customer. */
    private Account account;

    /** Initializes customer object and assigns its variables.
     * @param newName first name of the customer.
     * @param newSurname surname of the customer.
     * @param newSpendingLimit spending limit of the customer.
     * @param newBalance balance of the customers new account.
     * */
    public Customer(final String newName, final String newSurname, final double newSpendingLimit,
                    final double newBalance) {
        this.name = newName;
        this.surname = newSurname;
        this.account = new Account(newSurname, newSpendingLimit, newBalance);
    }

    /**
     * Empty constructor for creation of json objects. Do not use unless you set all variables manually afterwards.
     */
    public Customer() {

    }

    public String getName() { return name; }

    public void setName(final String newName) {
        this.name = newName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(final String newSurname) {
        this.surname = newSurname;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(final Account newAccount) {
        this.account = newAccount;
    }
}
