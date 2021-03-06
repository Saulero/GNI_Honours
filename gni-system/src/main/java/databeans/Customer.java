package databeans;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * @author Noel
 * @version 1
 * Databean used to send customer data over queues.
 */
public class Customer implements Serializable {
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
    private LocalDate dob;
    /** Social security number of the customer. */
    private long ssn;
    /** Username for logging into the account. */
    private String username;
    /** Password for logging into the account. */
    private String password;
    /** Account of the customer. */
    private Account account;
    /** Id of the customer. */
    private long customerId;
    private String[] guardians;
    private Long[] guardianIds;
    private boolean child;

    /** Initializes customer object and assigns its variables.
     * @param newInitials initials of the customer.
     * @param newName first name of the customer.
     * @param newSurname surname of the customer.
     * @param newEmail email of the customer.
     * @param newTelephoneNumber telephone number of the customer.
     * @param newAddress address of the customer.
     * @param newDob date of birth of the customer.
     * @param newSsn social security number of the customer.
     * @param newUsername Username of the customer.
     * @param newPassword Password of the customers new account.
     * */
    public Customer(final String newInitials, final String newName, final String newSurname, final String newEmail,
                    final String newTelephoneNumber, final String newAddress, final LocalDate newDob, final long newSsn,
                    final String newUsername, final String newPassword) {
        this.initials = newInitials;
        this.name = newName;
        this.surname = newSurname;
        this.email = newEmail;
        this.telephoneNumber = newTelephoneNumber;
        this.address = newAddress;
        this.dob = newDob;
        this.ssn = newSsn;
        this.username = newUsername;
        this.password = newPassword;
        this.account = new Account(newInitials + newSurname, 0, 0, false, 0.0);
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
     * @param newOverdraftLimit overdraft limit of the customer.
     * @param newBalance balance of the customers new account.
     * @param newId customerID
     * */
    public Customer(final String newInitials, final String newName, final String newSurname, final String newEmail,
                    final String newTelephoneNumber, final String newAddress, final LocalDate newDob, final long newSsn,
                    final double newOverdraftLimit, final double newBalance, final long newId) {
        this.initials = newInitials;
        this.name = newName;
        this.surname = newSurname;
        this.email = newEmail;
        this.telephoneNumber = newTelephoneNumber;
        this.address = newAddress;
        this.dob = newDob;
        this.ssn = newSsn;
        this.account = new Account(newSurname, newOverdraftLimit, newBalance, false, 0.0);
        this.customerId = newId;
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

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(final LocalDate newDob) {
        this.dob = newDob;
    }

    public long getSsn() {
        return ssn;
    }

    public void setSsn(final long newSsn) {
        this.ssn = newSsn;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final long newId) {
        customerId = newId;
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

    public String[] getGuardians() {
        return guardians;
    }

    public void setGuardians(final String[] newGuardians) {
        this.guardians = newGuardians;
        this.child = true;
    }

    public boolean isChild() {
        return child;
    }

    public Long[] getGuardianIds() {
        return guardianIds;
    }

    public void setGuardianIds(final Long[] newGuardianIds) {
        this.guardianIds = newGuardianIds;
    }

    public boolean minimalEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Customer customer = (Customer) o;

        if (ssn != customer.ssn) return false;
        if (customerId != customer.customerId) return false;
        if (initials != null ? !initials.equals(customer.initials) : customer.initials != null) return false;
        if (name != null ? !name.equals(customer.name) : customer.name != null) return false;
        if (surname != null ? !surname.equals(customer.surname) : customer.surname != null) return false;
        if (email != null ? !email.equals(customer.email) : customer.email != null) return false;
        if (telephoneNumber != null ? !telephoneNumber.equals(customer.telephoneNumber) : customer.telephoneNumber != null)
            return false;
        if (address != null ? !address.equals(customer.address) : customer.address != null) return false;
        if (dob != null ? !dob.equals(customer.dob) : customer.dob != null) return false;
        if (username != null ? !username.equals(customer.username) : customer.username != null) return false;
        return (password != null ? !password.equals(customer.password) : customer.password == null);
    }

    @Override
    public int hashCode() {
        int result = initials != null ? initials.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (surname != null ? surname.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (telephoneNumber != null ? telephoneNumber.hashCode() : 0);
        result = 31 * result + (address != null ? address.hashCode() : 0);
        result = 31 * result + (dob != null ? dob.hashCode() : 0);
        result = 31 * result + (int) (ssn ^ (ssn >>> 32));
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (account != null ? account.hashCode() : 0);
        result = 31 * result + (int) (customerId ^ (customerId >>> 32));
        return result;
    }
}
