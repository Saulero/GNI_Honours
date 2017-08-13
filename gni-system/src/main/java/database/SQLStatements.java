package database;

/**
 * @author Saul
 */
public final class SQLStatements {

    public static final String createNewAccount = "INSERT INTO ledger (id, account_number, name, overdraft_limit, balance) VALUES (?, ?, ?, ?, ?)";
    public static final String removeAccount = "DELETE FROM ledger WHERE id = ? AND account_number = ?";
    public static final String getAccountInformation = "SELECT * FROM ledger WHERE account_number = ?";
    public static final String updateBalance = "UPDATE ledger SET balance = ? WHERE account_number = ?";
    public static final String updateOverdraftLimit = "UPDATE ledger SET overdraft_limit = ? WHERE account_number = ?";
    public static final String getIncomingTransactionHistory = "SELECT * FROM transactions_in WHERE account_to = ?";
    public static final String getOutgoingTransactionHistory = "SELECT * FROM transactions_out WHERE account_from = ?";
    public static final String addIncomingTransaction = "INSERT INTO transactions_in (id, date, account_to, account_to_name, account_from, amount, new_balance, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String addOutgoingTransaction = "INSERT INTO transactions_out (id, date, account_to, account_to_name, account_from, amount, new_balance, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String getNextUserID = "SELECT MAX(id) FROM users";
    public static final String getHighestIncomingTransactionID = "SELECT MAX(id) FROM transactions_in";
    public static final String getHighestOutgoingTransactionID = "SELECT MAX(id) FROM transactions_out";
    public static final String getNextAccountID = "SELECT MAX(id) FROM ledger";
    public static final String createNewUser = "INSERT INTO users (id, initials, firstname, lastname, email, telephone_number, address, date_of_birth, social_security_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String addAccountToUser = "INSERT INTO accounts (user_id, account_number, primary_owner) VALUES (?, ?, ?)";
    public static final String getUserInformation = "SELECT * FROM users WHERE id = ?";
    public static final String getAccountNumbers = "SELECT * FROM accounts where user_id = ?";
    public static final String getPrimaryAccountNumbersCount = "SELECT count(*) FROM accounts WHERE user_id = ? AND primary_owner = true";
    public static final String getAccountNumberCount = "SELECT count(*) FROM ledger WHERE account_number = ?";
    public static final String getUserCount = "SELECT count(*) FROM users WHERE id = ?";
    public static final String getAuthenticationData1 = "SELECT * FROM authentication WHERE username = ?";
    public static final String getAuthenticationData2 = "SELECT * FROM authentication WHERE user_id = ?";
    public static final String createAuthenticationData = "INSERT INTO authentication (user_id, username, password) VALUES (?, ?, ?)";
    public static final String updateToken = "UPDATE authentication SET token = ?, token_validity = ? WHERE user_id = ?";
    public static final String updateTokenValidity = "UPDATE authentication SET token_validity = ? WHERE user_id = ?";
    public static final String getAccountLinkCount = "SELECT count(*) FROM accounts WHERE user_id = ? AND account_number = ?";
    public static final String getLoginUsernameCount = "SELECT count(*) FROM authentication WHERE username = ?";
    public static final String getCustomerIdFromUsername = "SELECT user_id FROM authentication WHERE username = ?";
    public static final String getUsernameFromCustomerId = "SELECT username FROM authentication WHERE user_id = ?";
    public static final String addPinCard = "INSERT INTO pin (account_number, user_id, card_number, pin_code, expiration_date, incorrect_attempts) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String getCustomerIdFromCardNumber = "SELECT user_id FROM pin WHERE card_number = ?";
    public static final String getPinCard = "SELECT * FROM pin WHERE card_number = ?";
    public static final String removePinCard = "DELETE FROM pin WHERE account_number = ? AND user_id = ? AND card_number = ? AND pin_code = ?";
    public static final String unblockPinCard = "UPDATE pin SET incorrect_attempts = 0 WHERE card_number = ?";
    public static final String incrementIncorrectAttempts = "UPDATE pin SET incorrect_attempts = incorrect_attempts + 1 WHERE card_number = ?";
    public static final String removeAccountCards = "DELETE FROM pin WHERE account_number = ?";
    public static final String removeCustomer = "DELETE FROM users WHERE id = ?";
    public static final String removeCustomerTokens = "DELETE FROM authentication WHERE user_id = ?";
    public static final String removeCustomerLinks = "DELETE FROM accounts WHERE user_id = ?";
    public static final String removeCustomerAccountLink = "DELETE FROM accounts WHERE user_id = ? AND account_number = ?";
    public static final String getHighestCardNumber = "SELECT MAX(card_number) FROM pin";
    public static final String removeAccountLinks = "DELETE FROM accounts WHERE account_number = ?";
    public static final String getAccountNumberUsingCardNumber = "SELECT account_number FROM pin WHERE card_number = ?";
    public static final String getAccountAccessList = "SELECT user_id FROM accounts WHERE account_number = ?";
    public static final String getPrimaryAccountOwner = "SELECT user_id FROM accounts WHERE account_number = ? AND primary_owner = true";
    public static final String getOverdraftAccounts = "SELECT DISTINCT account_to FROM transactions_in WHERE new_balance < amount AND date BETWEEN ? AND ? UNION SELECT DISTINCT account_from FROM transactions_out WHERE new_balance < 0 AND date BETWEEN ? AND ? UNION SELECT DISTINCT account_number FROM ledger where balance < 0";
    public static final String getAccountOverdraftTransactions = "SELECT * FROM transactions_in WHERE account_to = ? AND new_balance < amount AND date BETWEEN ? AND ? UNION SELECT * FROM transactions_out WHERE account_from = ? AND new_balance < 0 AND date BETWEEN ? AND ?";
    public static final String addRequestLog = "INSERT INTO request_logs (request_id, method, params, date, time) VALUES (?, ?, ?, ?, ?)";
    public static final String addErrorLog = "INSERT INTO error_logs (request_id, error_code, date, time, message, data) VALUES (?, ?, ?, ?, ?, ?)";
    public static final String getRequestLogs = "SELECT * FROM request_logs WHERE date BETWEEN ? AND ?";
    public static final String getErrorLogs = "SELECT * FROM error_logs WHERE date BETWEEN ? AND ?";

    // Create statements used for setting up the database
    public final static String createAccountsTable = "CREATE TABLE IF NOT EXISTS `accounts` ( `user_id` BIGINT(20) NOT NULL, `account_number` TEXT NOT NULL, `primary_owner` BOOLEAN NOT NULL);";
    public final static String dropAccountsTable = "DROP TABLE IF EXISTS `accounts`;";
    public final static String createLedgerTable = "CREATE TABLE IF NOT EXISTS `ledger` ( `id` BIGINT(20) NOT NULL, `account_number` TEXT NOT NULL, `name` TEXT NOT NULL, `overdraft_limit` DOUBLE NOT NULL, `balance` DOUBLE NOT NULL, PRIMARY KEY (id));";
    public final static String dropLedgerTable = "DROP TABLE IF EXISTS `ledger`;";
    public final static String createPinTable = "CREATE TABLE IF NOT EXISTS `pin`( `account_number` TEXT NOT NULL, `user_id` BIGINT(20) NOT NULL, `card_number` BIGINT(20) NOT NULL, `pin_code` TEXT NOT NULL, `expiration_date` DATE NOT NULL, `incorrect_attempts` BIGINT(20) NOT NULL, PRIMARY KEY (card_number));";
    public final static String dropPinTable = "DROP TABLE IF EXISTS `pin`;";
    public final static String createTransactionsInTable = "CREATE TABLE IF NOT EXISTS `transactions_in`( `id` BIGINT(20) NOT NULL, `date` DATE NOT NULL, `account_to` TEXT NOT NULL, `account_to_name` TEXT NOT NULL, `account_from` TEXT NOT NULL, `amount` DOUBLE NOT NULL, `new_balance` DOUBLE NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY (id));";
    public final static String dropTransactionsInTable = "DROP TABLE IF EXISTS `transactions_in`;";
    public final static String createTransactionsOutTable = "CREATE TABLE IF NOT EXISTS `transactions_out`( `id` BIGINT(20) NOT NULL, `date` DATE NOT NULL, `account_to` TEXT NOT NULL, `account_to_name` TEXT NOT NULL, `account_from` TEXT NOT NULL, `amount` DOUBLE NOT NULL, `new_balance` DOUBLE NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY (id));";
    public final static String dropTransactionsOutTable = "DROP TABLE IF EXISTS `transactions_out`;";
    public final static String createAuthTable = "CREATE TABLE IF NOT EXISTS `authentication`( `user_id` BIGINT(20) NOT NULL, `username` TEXT NOT NULL, `password` TEXT NOT NULL, `token` BIGINT(20), `token_validity` BIGINT(20), PRIMARY KEY (user_id));";
    public final static String dropAuthTable = "DROP TABLE IF EXISTS `authentication`;";
    public final static String createUsersTable = "CREATE TABLE IF NOT EXISTS `users`( `id` BIGINT(20) NOT NULL, `initials` TEXT NOT NULL, `firstname` TEXT NOT NULL, `lastname` TEXT NOT NULL, `email` TEXT NOT NULL, `telephone_number` TEXT NOT NULL, `address` TEXT NOT NULL, `date_of_birth` TEXT NOT NULL, `social_security_number` BIGINT(20) NOT NULL, PRIMARY KEY (id));";
    public final static String dropUsersTable = "DROP TABLE IF EXISTS `users`;";
    public final static String createRequestLogTable = "CREATE TABLE IF NOT EXISTS `request_logs`(`request_id` TEXT NOT NULL, `method` TEXT NOT NULL, `params` TEXT NOT NULL, `date` DATE NOT NULL, `time` TEXT NOT NULL);";
    public final static String dropRequestLogTable = "DROP TABLE IF EXISTS `request_logs`;";
    public final static String createErrorLogTable = "CREATE TABLE IF NOT EXISTS `error_logs`(`request_id` TEXT NOT NULL, `error_code` BIGINT(20) NOT NULL, `date` DATE NOT NULL, `time` TEXT NOT NULL, `message` TEXT NOT NULL, `data` TEXT NOT NULL);";
    public final static String dropErrorLogTable = "DROP TABLE IF EXISTS `error_logs`;";
    public final static String truncateAccountsTable = "TRUNCATE TABLE `accounts`";
    public final static String truncateLedgerTable = "TRUNCATE TABLE `ledger`";
    public final static String truncatePinTable = "TRUNCATE TABLE `pin`";
    public final static String truncateTransactionsInTable = "TRUNCATE TABLE `transactions_in`";
    public final static String truncateTransactionsOutTable = "TRUNCATE TABLE `transactions_out`";
    public final static String truncateAuthTable = "TRUNCATE TABLE `authentication`";
    public final static String truncateUsersTable = "TRUNCATE TABLE `users`";
    public final static String truncateRequestLogTable = "TRUNCATE TABLE `request_logs`";
    public final static String truncateErrorLogTable = "TRUNCATE TABLE `error_logs`";

}
