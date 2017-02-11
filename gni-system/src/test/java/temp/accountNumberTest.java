package temp;

import ledger.Ledger;
import ledger.NewAccount;

/**
 * @author Saul
 */
public class accountNumberTest {

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        NewAccount test1 = new NewAccount("MS van der Vies", 0, 0);
        NewAccount test2 = new NewAccount("M.S. van der Vies", 0, 0);
        String accountNumber1 = ledger.generateNewAccountNumber(test1);
        String accountNumber2 = ledger.generateNewAccountNumber(test2);
        System.out.println(accountNumber1);
        System.out.println(accountNumber2);
    }
}
