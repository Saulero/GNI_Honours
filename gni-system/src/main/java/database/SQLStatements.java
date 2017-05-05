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
    public static final String getLoginUsernameCount = "SELECT count(*) FROM authentication WHERE username = ?";
    public static final String addPinCard = "INSERT INTO pin (account_number, user_id, card_number, pin_code, expiration_date) VALUES (?, ?, ?, ?, ?)";
    public static final String getCustomerIdFromCardNumber = "SELECT user_id FROM pin WHERE card_number = ?";
    public static final String getPinCard = "SELECT * FROM pin WHERE card_number = ?";
    public static final String removePinCard = "DELETE FROM pin WHERE account_number = ? AND user_id = ? AND card_number = ? AND pin_code = ?";
    public static final String getHighestCardNumber = "SELECT MAX(card_number) FROM pin";

    // Create statements used for setting up the database
    public final static String createAccountsTable = "CREATE TABLE `accounts` ( `user_id` BIGINT(20) NOT NULL, `account_number` TEXT NOT NULL);";
    public final static String dropAccountsTable = "DROP TABLE IF EXISTS `accounts`;";
    public final static String createLedgerTable = "CREATE TABLE `ledger` ( `id` BIGINT(20) NOT NULL, `account_number` TEXT NOT NULL, `name` TEXT NOT NULL, `spending_limit` DOUBLE NOT NULL, `balance` DOUBLE NOT NULL, PRIMARY KEY (id));";
    public final static String dropLedgerTable = "DROP TABLE IF EXISTS `ledger`;";
    public final static String createPinTable = "CREATE TABLE `pin` ( `account_number` TEXT NOT NULL, `user_id` BIGINT(20) NOT NULL, `card_number` TEXT NOT NULL, `pin_code` TEXT NOT NULL, `expiration_date` DATE NOT NULL, PRIMARY KEY(card_number));";
    public final static String dropPinTable = "DROP TABLE IF EXISTS `pin`;";
    public final static String createTransactionsInTable = "CREATE TABLE `transactions_in` ( `id` BIGINT(20) NOT NULL, `timestamp` BIGINT(20) NOT NULL, `account_to` TEXT NOT NULL, `account_to_name` TEXT NOT NULL, `account_from` TEXT NOT NULL, `amount` DOUBLE NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY (id));";
    public final static String dropTransactionsInTable = "DROP TABLE IF EXISTS `transactions_in`;";
    public final static String createTransactionsOutTable = "CREATE TABLE `transactions_out` ( `id` BIGINT(20) NOT NULL, `timestamp` BIGINT(20) NOT NULL, `account_to` TEXT NOT NULL, `account_to_name` TEXT NOT NULL, `account_from` TEXT NOT NULL, `amount` DOUBLE NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY (id));";
    public final static String dropTransactionsOutTable = "DROP TABLE IF EXISTS `transactions_out`;";
    public final static String createAuthTable = "CREATE TABLE `authentication` ( `user_id` BIGINT(20) NOT NULL, `username` TEXT NOT NULL, `password` TEXT NOT NULL, `token` BIGINT(20), `token_validity` BIGINT(20), PRIMARY KEY (user_id));";
    public final static String dropAuthTable = "DROP TABLE IF EXISTS `authentication`;";
    public final static String createUsersTable = "CREATE TABLE `users` ( `id` BIGINT(20) NOT NULL, `initials` TEXT NOT NULL, `firstname` TEXT NOT NULL, `lastname` TEXT NOT NULL, `email` TEXT NOT NULL, `telephone_number` TEXT NOT NULL, `address` TEXT NOT NULL, `date_of_birth` TEXT NOT NULL, `social_security_number` BIGINT(20) NOT NULL, PRIMARY KEY (id));";
    public final static String dropUsersTable = "DROP TABLE IF EXISTS `users`;";
}
