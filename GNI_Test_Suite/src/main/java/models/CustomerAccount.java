package models;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class CustomerAccount {
    private String name;
    private String surname;
    private String initials;
    private String dob;
    private String ssn;
    private String address;
    private String telephoneNumber;
    private String email;
    private String username;
    private String password;
    private Set<BankAccount> bankAccounts;
    private Set<PinCard> pinCards;
    private String authToken;

    public CustomerAccount(String name, String surname, String initials, String dob, String ssn, String address, String telephoneNumber, String email, String username, String password) {
        this.name = name;
        this.surname = surname;
        this.initials = initials;
        this.dob = dob;
        this.ssn = ssn;
        this.address = address;
        this.telephoneNumber = telephoneNumber;
        this.email = email;
        this.username = username;
        this.password = password;
        this.bankAccounts = new HashSet<BankAccount>();
        this.pinCards = new HashSet<PinCard>();
        this.authToken = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }

    public void setTelephoneNumber(String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<BankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public void setBankAccounts(Set bankAccounts) {
        bankAccounts = bankAccounts;
    }

    public void addBankAccount(BankAccount account){
        bankAccounts.add(account);
    }

    public Set<PinCard> getPinCards() {
        return pinCards;
    }

    public void setPinCards(Set<PinCard> pinCards) {
        this.pinCards = pinCards;
    }

    public  void addPinCard(PinCard pincard){
        this.pinCards.add(pincard);
    }


    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }


}
