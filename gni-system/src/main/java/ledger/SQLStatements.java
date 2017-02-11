package ledger;

/**
 * @author Saul
 */
public final class SQLStatements {

    public static final String createNewAccount = "INSERT INTO ledger (id, account_number, name, spending_limit, balance) VALUES (?, ?, ?, ?, ?)";
    public static final String getNextUserID = "SELECT MAX(id) FROM public.course";
    public static final String getAccountInformation = "SELECT * FROM ledger WHERE ledger.account_number = ?";
}
