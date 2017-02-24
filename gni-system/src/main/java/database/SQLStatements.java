package database;

/**
 * @author Saul
 */
public final class SQLStatements {

    public static final String createNewAccount = "INSERT INTO ledger (id, account_number, name, spending_limit, balance) VALUES (?, ?, ?, ?, ?)";
    public static final String getAccountInformation = "SELECT * FROM ledger WHERE ledger.account_number = ?";
    public static final String updateBalance = "UPDATE ledger SET ledger.spending_limit = ?, ledger.balance = ? WHERE ledger.account_number = ?";
    public static final String getIncomingTransactionHistory = "SELECT * FROM transactions_in WHERE transactions_in.account_to = ?";
    public static final String getOutgoingTransactionHistory = "SELECT * FROM transactions_out WHERE transactions_out.account_from = ?";
    public static final String addIncomingTransaction = "INSERT INTO transactions_in (id, timestamp, account_to, account_from, amount) VALUES (?, ?, ?, ?, ?)";
    public static final String addOutgoingTransaction = "INSERT INTO transactions_out (id, timestamp, account_to, account_from, amount) VALUES (?, ?, ?, ?, ?)";
    public static final String getNextUserID = "SELECT MAX(id) FROM users";
    public static final String getHighestIncomingTransactionID = "SELECT MAX(id) FROM transactions_in";
    public static final String getHighestOutgoingTransactionID = "SELECT MAX(id) FROM transactions_out";
    public static final String getNextAccountID = "SELECT MAX(id) FROM ledger";
}
