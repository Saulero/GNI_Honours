package databeans;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author Saul
 */
public class BirthdayInterestPayment implements Serializable {

    private long userId;
    private LocalDate interestDate;
    private LocalDate adjustedDob;
    private String accountNumber;

    public BirthdayInterestPayment(final long newUserId, final LocalDate newInterestDate, final LocalDate newAdjustedDob, final String newAccountNumber) {
        this.userId = newUserId;
        this.interestDate = newInterestDate;
        this.adjustedDob = newAdjustedDob;
        this.accountNumber = newAccountNumber;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(final long newUserId) {
        this.userId = newUserId;
    }

    public LocalDate getInterestDate() {
        return interestDate;
    }

    public void setInterestDate(final LocalDate newInterestDate) {
        this.interestDate = newInterestDate;
    }

    public LocalDate getAdjustedDob() {
        return adjustedDob;
    }

    public void setAdjustedDob(final LocalDate newAdjustedDob) {
        this.adjustedDob = newAdjustedDob;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(final String newAccountNumber) {
        this.accountNumber = newAccountNumber;
    }
}
