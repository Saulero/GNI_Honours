package database;

/**
 * @author Saul
 */
public final class SQLStatements {

    public static final String createNewAccount = "INSERT INTO ledger (id, account_number, name, spending_limit, balance) VALUES (?, ?, ?, ?, ?)";
    public static final String getAccountInformation = "SELECT * FROM ledger WHERE ledger.account_number = ?";
    public static final String updateBalance = "UPDATE ledger SET ledger.spending_limit = ?, ledger.balance = ? WHERE ledger.account_number = ?";
    // TODO Update database settings/names
    public static final String getTransactionHistory = "SELECT * FROM transactions WHERE transactions.source_account = ? OR transactions.destination_account = ?";
    public static final String getNextUserID = "SELECT MAX(id) FROM users";
    public static final String getNextTransactionID = "SELECT MAX(id) FROM transactions";
    public static final String getNextAccountID = "SELECT MAX(id) FROM ledger";
}
