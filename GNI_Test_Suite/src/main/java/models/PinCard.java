package models;

import methods.server.CalanderUTIL;

import java.util.Calendar;

public class PinCard {
    private BankAccount bankAccount;
    private String pinCardNumber;
    private String pinCode;
    private Calendar expirationDate;
    private int failedAttempts = 0;
    private Boolean blocked = false;

    // Temporary method. While expirationDate is not part of the protocol.
    public PinCard(BankAccount bankAccount, String pinCardNumber, String pinCode, Calendar now) {
        this.bankAccount = bankAccount;
        this.pinCardNumber = pinCardNumber;
        this.pinCode = pinCode;
        this.expirationDate = now;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(BankAccount bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getPinCardNumber() {
        return pinCardNumber;
    }

    public void setPinCardNumber(String pinCardNumber) {
        this.pinCardNumber = pinCardNumber;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public Calendar getExpirationDate() {
        return expirationDate;
    }

    public String getExpirationDateString() {
        return CalanderUTIL.CalanderToDateString(this.expirationDate);
    }

    public void setExpirationDate(Calendar expirationDate) {
        this.expirationDate = expirationDate;
    }


    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(Boolean blocked) {
        this.blocked = blocked;
    }
}
