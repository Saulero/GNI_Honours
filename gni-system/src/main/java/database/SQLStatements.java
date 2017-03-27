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
    public static final String addIncomingTransaction = "INSERT INTO transactions_in (id, timestamp, account_to, account_to_name, account_from, amount, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
    public static final String addOutgoingTransaction = "INSERT INTO transactions_out (id, timestamp, account_to, account_to_name, account_from, amount, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
    public static final String getNextUserID = "SELECT MAX(id) FROM users";
    public static final String getHighestIncomingTransactionID = "SELECT MAX(id) FROM transactions_in";
    public static final String getHighestOutgoingTransactionID = "SELECT MAX(id) FROM transactions_out";
    public static final String getNextAccountID = "SELECT MAX(id) FROM ledger";
    public static final String createNewUser = "INSERT INTO users (id, initials, firstname, lastname, email, telephone_number, address, date_of_birth, social_security_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String addAccountToUser = "INSERT INTO accounts (user_id, account_number) VALUES (?, ?)";
    public static final String getUserInformation = "SELECT * FROM users WHERE users.id = ?";
    public static final String getAccountNumbers = "SELECT * FROM accounts where accounts.user_id = ?";
    public static final String getAccountNumberCount = "SELECT count(*) FROM ledger WHERE account_number = ?";
    public static final String getUserCount = "SELECT count(*) FROM users WHERE users.id = ?";
    public static final String getAuthenticationData1 = "SELECT * FROM authentication WHERE authentication.username = ?";
    public static final String getAuthenticationData2 = "SELECT * FROM authentication WHERE authentication.user_id = ?";
    public static final String createAuthenticationData = "INSERT INTO authentication (user_id, username, password) VALUES (?, ?, ?)";
    public static final String updateToken = "UPDATE authentication SET authentication.token = ?, authentication.token_validity = ? WHERE authentication.user_id = ?";
    public static final String updateTokenValidity = "UPDATE authentication SET authentication.token_validity = ? WHERE authentication.user_id = ?";
    public static final String getAccountLinkCount = "SELECT count(*) FROM accounts WHERE user_id = ? AND account_number = ?";
}
