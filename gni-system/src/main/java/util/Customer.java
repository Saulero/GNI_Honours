package util;

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
    /** accountNumber of the customer. */
    private String accountNumber;
    /** Indicates if the customer has been entered into the system */
    private boolean enrolled;

    /** Initializes customer object and assigns its variables.
     * @param newName first name of the customer.
     * @param newSurname surname of the customer.
     * @param newAccountNumber Account number to be assigned to the customer.
     * @param newEnrolled Indicates if the customer has been enrolled in the system yet.
     * */
    public Customer(final String newName, final String newSurname,
                    final String newAccountNumber, final boolean newEnrolled) {
        this.name = newName;
        this.surname = newSurname;
        this.accountNumber = newAccountNumber;
        this.enrolled = newEnrolled;
    }

    /**
     * Empty constructor for creation of json objects. Do not use unless you set all variables manually afterwards.
     */
    public Customer() {}

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

    public boolean getEnrolled() { return enrolled; }

    public void setEnrolled(final boolean newEnrolled) { this.enrolled = newEnrolled; }
}
