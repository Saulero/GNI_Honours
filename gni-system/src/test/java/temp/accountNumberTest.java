package temp;

import ledger.Ledger;
import ledger.Account;

/**
 * @author Saul
 */
public class accountNumberTest {

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        Account test1 = new Account("MS van der Vies", 0, 0);
        Account test2 = new Account("M.S. van der Vies", 0, 0);
        String accountNumber1 = ledger.generateNewAccountNumber(test1);
        String accountNumber2 = ledger.generateNewAccountNumber(test2);
        System.out.println(accountNumber1);
        System.out.println(accountNumber2);
    }
}
