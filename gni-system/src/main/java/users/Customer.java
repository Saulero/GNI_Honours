package users;

/**
 * @author Noel
 * @version 1
 * Databean used to send customer data over queues.
 */
public final class Customer {
    /** First name of the customer. */
    private String name;
    /** Surname of the customer. */
    private String surname;
    /** accountNumber of the customer. */
    private String accountNumber;

    /** Initializes customer object and assigns its variables.
     * @param newName first name of the customer.
     * @param newSurname surname of the customer.
     * @param newAccountNumber Account number to be assigned to the customer.
     * */
    public Customer(final String newName, final String newSurname,
                    final String newAccountNumber) {
        this.name = newName;
        this.surname = newSurname;
        this.accountNumber = newAccountNumber;
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

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        this.accountNumber = newAccountNumber;
    }
}
