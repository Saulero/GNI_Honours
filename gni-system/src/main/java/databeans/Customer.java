package databeans;

import java.io.Serializable;
import java.sql.Date;

/**
 * @author Noel
 * @version 1
 * Databean used to send customer data over queues.
 */
public final class Customer implements Serializable {
    /** Initials of the customer. */
    private String initials;
    /** First name of the customer. */
    private String name;
    /** Surname of the customer. */
    private String surname;
    /** Email of the customer. */
    private String email;
    /** Telephone number of the customer. */
    private String telephoneNumber;
    /** Address of the customer. */
    private String address;
    /** Date of birth of the customer. */
    private String dob;
    /** Social security number of the customer. */
    private Long ssn;
    /** Username for logging into the account. */
    private String username;
    /** Password for logging into the account. */
    private String password;
    /** Account of the customer. */
    private Account account;
    /** Id of the customer. */
    private Long Id;

    /** Initializes customer object and assigns its variables.
     * @param newInitials initials of the customer.
     * @param newName first name of the customer.
     * @param newSurname surname of the customer.
     * @param newEmail email of the customer.
     * @param newTelephoneNumber telephone number of the customer.
     * @param newAddress address of the customer.
     * @param newDob date of birth of the customer.
     * @param newSsn social security number of the customer.
     * @param newSpendingLimit spending limit of the customer.
     * @param newBalance balance of the customers new account.
     * */
    public Customer(final String newInitials, final String newName, final String newSurname, final String newEmail,
                    final String newTelephoneNumber, final String newAddress, final String newDob, final Long newSsn,
                    final double newSpendingLimit, final double newBalance) {
        this.initials = newInitials;
        this.name = newName;
        this.surname = newSurname;
        this.email = newEmail;
        this.telephoneNumber = newTelephoneNumber;
        this.address = newAddress;
        this.dob = newDob;
        this.ssn = newSsn;
        this.account = new Account(newInitials + newSurname, newSpendingLimit, newBalance);
    }

    /** Initializes customer object and assigns its variables.
     * @param newInitials initials of the customer.
     * @param newName first name of the customer.
     * @param newSurname surname of the customer.
     * @param newEmail email of the customer.
     * @param newTelephoneNumber telephone number of the customer.
     * @param newAddress address of the customer.
     * @param newDob date of birth of the customer.
     * @param newSsn social security number of the customer.
     * @param newSpendingLimit spending limit of the customer.
     * @param newBalance balance of the customers new account.
     * */
    public Customer(final String newInitials, final String newName, final String newSurname, final String newEmail,
                    final String newTelephoneNumber, final String newAddress, final String newDob, final Long newSsn,
                    final double newSpendingLimit, final double newBalance, final Long newId) {
        this.initials = newInitials;
        this.name = newName;
        this.surname = newSurname;
        this.email = newEmail;
        this.telephoneNumber = newTelephoneNumber;
        this.address = newAddress;
        this.dob = newDob;
        this.ssn = newSsn;
        this.account = new Account(newSurname, newSpendingLimit, newBalance);
        this.Id = newId;
    }

    /**
     * Empty constructor for creation of json objects. Do not use unless you set all variables manually afterwards.
     */
    public Customer() {

    }

    public String getName() {
        return name;
    }

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

    public String getInitials() {
        return initials;
    }

    public void setInitials(final String newInitials) {
        this.initials = newInitials;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String newEmail) {
        this.email = newEmail;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(final String newTelephoneNumber) {
        this.telephoneNumber = newTelephoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(final String newAddress) {
        this.address = newAddress;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(final String newDob) {
        this.dob = newDob;
    }

    public Long getSsn() {
        return ssn;
    }

    public void setSsn(final Long newSsn) {
        this.ssn = newSsn;
    }

    public Long getId() {
        return Id;
    }

    public void setId(final Long newId) {
        Id = newId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String newUsername) {
        username = newUsername;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String newPassword) {
        password = newPassword;
    }
}
