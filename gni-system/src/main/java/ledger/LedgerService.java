package ledger;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
import database.SQLStatements;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.RequestType;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import javax.xml.crypto.Data;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

import static database.SQLStatements.*;
import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 * @version 1
 */
@RequestMapping("/ledger")
class LedgerService {

    /** Database connection pool containing persistent database connections. */
    private ConnectionPool db;
    /** Connection to the System Information Service.*/
    private HttpClient systemInformationClient;
     /** Connection to the pin service. */
     private HttpClient pinClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Ledger Service. */
    private static final String PREFIX = "[Ledger]              :";
    /** Interest rate that the bank charges every month to customers that are overdraft. */
    private static final double MONTHLY_OVERDRAFT_RATE = 0.00797;
    /** Interest rate for a savings balance of up to the tier 1 cap. */
    private static final double TIER_1_INTEREST_RATE = 0.15;
    /** Cap of the tier 1 interest rate. */
    private static final int TIER_1_CAP = 25000;
    /** Interest rate for a savings balance from tier 1 cap until tier 2 cap. */
    private static final double TIER_2_INTEREST_RATE = 0.15;
    /** Cap of the tier 2 interest rate. */
    private static final int TIER_2_CAP = 75000;
    /** Interest rate for a savings balance of more than tier 2 cap. */
    private static final double TIER_3_INTEREST_RATE = 0.20;
    /** Account number where fees are transferred to. */
    private static final String GNI_ACCOUNT = "NL52GNIB3676451168";

    /**
     * Constructor.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    LedgerService(final int servicePort, final String serviceHost,
                      final int sysInfoPort, final String sysInfoHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
        this.db = new ConnectionPool();
        this.jsonConverter = new Gson();
        sendServiceInformation(servicePort, serviceHost);
    }

    /**
     * Method that sends the service information of this service to the SystemInformationService.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     */
    private void sendServiceInformation(final int servicePort, final String serviceHost) {
        ServiceInformation serviceInfo = new ServiceInformation(servicePort, serviceHost, ServiceType.LEDGER_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", jsonConverter.toJson(serviceInfo), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        System.err.println("Problem with connection to the SystemInformationService.");
                        System.err.println("Shutting down the Ledger service.");
                        System.exit(1);
                    }
                });
    }

    /**
     * Method that initializes all connections to other services once it knows their addresses.
     * @param callback Callback to the source of the request.
     * @param systemInfo Json string containing all System Information.
     */
    @RequestMapping(value = "/start", method = RequestMethod.PUT)
    public void startService(final Callback<String> callback, @RequestParam("sysInfo") final String systemInfo) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(systemInfo), MessageWrapper.class);

        SystemInformation sysInfo = (SystemInformation) messageWrapper.getData();
        ServiceInformation pin = sysInfo.getPinServiceInformation();
        pinClient = httpClientBuilder().setHost(pin.getServiceHost()).setPort(pin.getServicePort()).buildAndStart();
        System.out.printf("%s Initialization of Ledger service connections complete.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
    }

    /**
     * Receives a request for a new account for a customer and sends the data back to UserService,
     * so that the new account may be properly linked to the customer.
     * @param callback Used to send a reply to the request source.
     * @param body JSON String representing customer information
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void newAccountListener(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Account newAccount = gson.fromJson(body, Account.class);
        System.out.printf("%s Received account creation request for customer with name: %s\n", PREFIX,
                newAccount.getAccountHolderName());

        // Method call
        newAccount = createNewAccount(newAccount);

        if (newAccount != null) {
            System.out.printf("%s Added user %s with accountNumber %s to ledger, sending callback.\n", PREFIX,
                    newAccount.getAccountHolderName(), newAccount.getAccountNumber());
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", newAccount)));
        } else {
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to ledger database.")));
        }
    }

    /**
     * Creates a new account for a customer and sends the data back to UserService,
     * so that the new account may be properly linked to the customer.
     * @param newAccount Object representing customer information
     * @return account number that was generated
     */
    Account createNewAccount(final Account newAccount) {
        newAccount.setAccountNumber(generateNewAccountNumber(newAccount));
        try {
            SQLConnection connection = db.getConnection();
            long newID = connection.getNextID(getNextAccountID);
            PreparedStatement ps = connection.getConnection().prepareStatement(createNewAccount);
            ps.setLong(1, newID);                               // id
            ps.setString(2, newAccount.getAccountNumber());     // account_number
            ps.setString(3, newAccount.getAccountHolderName()); // name
            ps.setDouble(4, newAccount.getOverdraftLimit());    // overdraft_limit
            ps.setDouble(5, newAccount.getBalance());           // balance
            ps.setBoolean(6, false);                         // savings_active
            ps.setDouble(7, 0.0);                            // savings balance

            ps.executeUpdate();
            ps.close();
            db.returnConnection(connection);

            return newAccount;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a new account number based on specific customer information,
     * also checks that the account number doesn't already exist.
     * @param newAccount Account Object containing customer information
     * @return The new account number
     */
    String generateNewAccountNumber(final Account newAccount) {
        int modifier = 0;
        String accountNumber = attemptAccountNumberGeneration(newAccount.getAccountHolderName(), modifier);
        while (modifier < 100 && getAccountInfo(accountNumber) != null) {
            modifier++;
            accountNumber = attemptAccountNumberGeneration(newAccount.getAccountHolderName(), modifier);
        }
        return accountNumber;
    }

    /**
     * Generates a single account number, based on a name and a modifier,
     * which are specified by the calling method.
     * @param name Customer name to be used
     * @param modifier Modifier to be used
     * @return The generated account number
     */
    String attemptAccountNumberGeneration(final String name, final int modifier) {
        String accountNumber = "NL";
        if (modifier < 10) {
            accountNumber += "0";
        }
        accountNumber += modifier + "GNIB";

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((sanitizeName(name) + modifier).getBytes());
            byte[] digest = md.digest();
            for (int i = 0; i < 10; i++) {
                accountNumber += Math.abs(digest[i] % 10);
            }
            return accountNumber;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sanitizes a string.
     * Deleted all spaces, non-letter characters, converts to lower case.
     * @param inputString String to be sanitized
     * @return Sanitized String
     */
    private String sanitizeName(final String inputString) {
        return inputString.replaceAll("[^a-zA-Z]", "").toLowerCase();
    }

    /**
     * Gets all data for a specific account number from the database.
     * @param accountNumber The account number to retrieve the information for
     * @return The account information
     */
    Account getAccountInfo(final String accountNumber) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(getAccountInformation);
            ps.setString(1, accountNumber);     // account_number
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                double overdraftLimit = rs.getDouble("overdraft_limit");
                double balance = rs.getDouble("balance");
                boolean savingsActive = rs.getBoolean("savings_active");
                double savingsBalance = rs.getDouble("savings_balance");
                Account account = new Account(name, overdraftLimit, balance, savingsActive, savingsBalance);
                account.setAccountNumber(accountNumber);

                rs.close();
                ps.close();
                db.returnConnection(connection);
                return account;
            } else {
                rs.close();
                ps.close();
                db.returnConnection(connection);
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Receives a request for the removal of a customer account, if successful this account will be removed from
     * the system. Calls the exception handler which will process the request.
     * @param callback Used to send the result of the request back to the request source.
     * @param accountNumber AccountNumber of the account that should be removed from the system.
     * @param customerId CustomerId of the User that requested the removal of the account.
     */
    @RequestMapping(value = "/account/remove", method = RequestMethod.PUT)
    public void processRemoveAccountRequest(final Callback<String> callback,
                                            final @RequestParam("accountNumber") String accountNumber,
                                            final @RequestParam("customerId") String customerId) {
        System.out.printf("%s Received account removal request for accountNumber %s\n", PREFIX, accountNumber);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountRemovalExceptions(accountNumber, customerId, callbackBuilder);
    }

    /**
     * Will try to execute the accountRemoval and then send a callback indicating the successful removal of the
     * account, if an exception is thrown the request will be rejected.
     * @param accountNumber AccountNumber of the account that should be removed from the system.
     * @param customerId Id of the customer that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void handleAccountRemovalExceptions(final String accountNumber, final String customerId,
                                                final CallbackBuilder callbackBuilder) {
        try {
            Account account = getAccountInfo(accountNumber);
            if (account != null) {
                if (account.getBalance() < 0) {
                    throw new IllegalArgumentException("Account can't be closed, since it has a negative balance.");
                }
            } else {
                throw new SQLException();
            }
            doAccountRemoval(accountNumber, customerId);
            sendAccountRemovalCallback(accountNumber, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to Ledger database.")));
        } catch (IllegalArgumentException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Unknown error occurred.", e.getMessage())));
        }
    }

    /**
     * Removes an account from the system.
     * @param accountNumber AccountNumber of the account to remove.
     * @param customerId CustomerId of the owner of the account.
     * @throws SQLException Thrown when connection to the database fails, will cause a rejection of the request.
     */
    private void doAccountRemoval(final String accountNumber, final String customerId) throws SQLException {
        SQLConnection databaseConnection = db.getConnection();
        PreparedStatement removeAccount = databaseConnection.getConnection()
                .prepareStatement(SQLStatements.removeAccount);
        removeAccount.setLong(1, Long.parseLong(customerId));
        removeAccount.setString(2, accountNumber);
        removeAccount.execute();
        removeAccount.close();
        db.returnConnection(databaseConnection);
    }

    private void sendAccountRemovalCallback(final String accountNumber, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully removed account %s, sending callback.\n", PREFIX, accountNumber);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", accountNumber)));
    }

    /**
     * Overwrites account information for a specific account,
     * in case a transaction is successfully processed.
     * @param account The account to overwrite, containing the new data
     */
    void updateBalance(final Account account) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateBalance);
            ps.setDouble(1, account.getBalance());
            ps.setString(2, account.getAccountNumber());
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Overwrites account information with a new overdraft limit.
     * @param account The account to overwrite, containing the new data
     * @throws SQLException SQLException
     */
    void updateOverdraftLimit(final Account account) throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement ps = connection.getConnection().prepareStatement(updateOverdraftLimit);
        ps.setDouble(1, account.getOverdraftLimit());
        ps.setString(2, account.getAccountNumber());
        ps.executeUpdate();

        ps.close();
        db.returnConnection(connection);
    }

    // TODO THIS SERVICE IS NOT ALLOWED TO USE OTHER SERVICE'S THEIR DATABASES
    /**
     * Adds a transaction to either the incoming transaction log, or the outgoing transaction log,
     * depending on the incoming flag.
     * @param transaction Transaction to add
     * @param incoming Incoming flag (true for incoming, false for outgoing)
     */
    void addTransaction(final Transaction transaction, final boolean incoming) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps;
            if (incoming) {
                ps = connection.getConnection().prepareStatement(addIncomingTransaction);
            } else {
                ps = connection.getConnection().prepareStatement(addOutgoingTransaction);
            }

            ps.setLong(1, transaction.getTransactionID());
            ps.setDate(2, java.sql.Date.valueOf(transaction.getDate()));
            ps.setString(3, transaction.getDestinationAccountNumber());
            ps.setString(4, transaction.getDestinationAccountHolderName());
            ps.setString(5, transaction.getSourceAccountNumber());
            ps.setDouble(6, transaction.getTransactionAmount());
            ps.setDouble(7, transaction.getNewBalance());
            ps.setDouble(8, transaction.getNewSavingsBalance());
            ps.setString(9, transaction.getDescription());
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Collects the current highest ids from both transaction tables and returns the highest.
     * @return The highest current transaction id
     */
    long getHighestTransactionID() {
        SQLConnection connection = db.getConnection();
        long maxIncoming = connection.getNextID(getHighestIncomingTransactionID);
        long maxOutgoing = connection.getNextID(getHighestOutgoingTransactionID);
        db.returnConnection(connection);

        return Math.max(0, Math.max(maxIncoming, maxOutgoing));
    }

    /**
     * Receives a request to process an incoming transaction.
     * @param callback Used to send a reply to the request source.
     * @param body JSON String representing a Transaction
     */
    @RequestMapping(value = "/transaction/in", method = RequestMethod.PUT)
    public void incomingTransactionListener(final Callback<String> callback,
                                            final @RequestParam("request") String body) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.printf("%s Received an incoming transaction request.\n", PREFIX);
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);
        processIncomingTransaction(transaction, callbackBuilder);
    }

    /**
     * Processes an incoming transaction.
     * Checks if the account exists and then processes the transaction if it does.
     * @param transaction Object representing a Transaction
     */
    void processIncomingTransaction(final Transaction transaction, final CallbackBuilder callbackBuilder) {
        systemInformationClient.getAsync("/services/systemInfo/date", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {

                    LocalDate date = (LocalDate) messageWrapper.getData();
                    Account account = getAccountInfo(transaction.getDestinationAccountNumber());
                    if (account != null) {
                        // Update the object
                        account.processDeposit(transaction);

                        // Update the database
                        updateBalance(account);
                        transaction.setNewBalance(account.getBalance());
                        updateSavingsBalance(account);
                        transaction.setNewSavingsBalance(account.getSavingsBalance());

                        // Update Transaction log
                        transaction.setTransactionID(getHighestTransactionID());
                        transaction.setDate(date);
                        addTransaction(transaction, true);

                        transaction.setProcessed(true);
                        transaction.setSuccessful(true);
                    } else {
                        transaction.setProcessed(true);
                        transaction.setSuccessful(false);
                    }
                    sendIncomingTransactionCallback(transaction, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                System.out.printf("%s Processing Incoming transaction failed, body: %s\n\n\n\n", PREFIX, body);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                        500, "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests")));
            }
        });
    }

    private void sendIncomingTransactionCallback(final Transaction transaction, final CallbackBuilder callbackBuilder) {
        if (transaction.isSuccessful()) {
            System.out.printf("%s Successfully processed incoming transaction, sending callback.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", transaction)));
        } else {
            System.out.printf("%s Incoming transaction was not successful, sending callback.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        }
    }

    /**
     * Receives a request to process an outgoing transaction.
     * @param callback Used to send a reply to the request source.
     * @param requestJson JSON String representing a Transaction
     * @param customerId ID of the customer makin the request, for authorization purposes
     */
    @RequestMapping(value = "/transaction/out", method = RequestMethod.PUT)
    public void outgoingTransactionListener(final Callback<String> callback,
                                            @RequestParam("request") final String requestJson,
                                            @RequestParam("customerId") final String customerId,
                                            @RequestParam("override") final Boolean override) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.printf("%s Received outgoing transaction request for customer %s.\n", PREFIX, customerId);
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(requestJson, Transaction.class);
        boolean customerIsAuthorized = getCustomerAuthorization(transaction.getSourceAccountNumber(), customerId);
        processOutgoingTransaction(transaction, customerIsAuthorized, override, callbackBuilder);
    }

    /**
     * Processes an outgoing transaction.
     * Checks if the account making the transaction is allowed to do this. (has a high enough overdraft limit)
     * @param transaction Object representing a Transaction request.
     * @param customerIsAuthorized boolean to signify if the outgoing transaction is allowed
     */
    void processOutgoingTransaction(final Transaction transaction, final boolean customerIsAuthorized,
                                    final boolean override, final CallbackBuilder callbackBuilder) {
        systemInformationClient.getAsync("/services/systemInfo/date", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {

                    LocalDate date = (LocalDate) messageWrapper.getData();
                    Account account;
                    String sourceAccountNumber = transaction.getSourceAccountNumber();
                    if (sourceAccountNumber.endsWith("S")) {
                        account = getAccountInfo(sourceAccountNumber.substring(0, sourceAccountNumber.length() - 1));
                    } else {
                        account = getAccountInfo(sourceAccountNumber);
                    }
                    if (account != null && (account.withdrawTransactionIsAllowed(transaction) || override)
                            && customerIsAuthorized) {
                        // Update the object
                        account.processWithdraw(transaction);

                        // Update the database
                        updateBalance(account);
                        transaction.setNewBalance(account.getBalance());
                        updateSavingsBalance(account);
                        transaction.setNewSavingsBalance(account.getSavingsBalance());

                        /// Update Transaction log
                        transaction.setTransactionID(getHighestTransactionID());
                        transaction.setDate(date);
                        addTransaction(transaction, false);

                        transaction.setProcessed(true);
                        transaction.setSuccessful(true);
                    } else {
                        transaction.setProcessed(true);
                        transaction.setSuccessful(false);
                    }
                    sendOutgoingTransactionCallback(transaction, customerIsAuthorized, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                System.out.printf("%s Processing Outgoing transaction failed, body: %s\n\n\n\n", PREFIX, body);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                        500, "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests")));
            }
        });
    }


    private void sendOutgoingTransactionCallback(final Transaction transaction, final boolean customerIsAuthorized, final CallbackBuilder callbackBuilder) {
        if (transaction.isSuccessful()) {
            System.out.printf("%s Successfully processed outgoing transaction, sending callback.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", transaction)));
        } else {
            if (!customerIsAuthorized) {
                System.out.printf("%s Customer is not authorized to make transactions from this account.\n", PREFIX);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419, "The user is not authorized to perform this action.", "Customer is not authorized to make transactions from this account")));
            } else {
                System.out.printf("%s Outgoing transaction was not successful, sending callback.\n", PREFIX);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "There is probably not enough balance in the account.")));
            }
        }
    }

    /**
     * Determines whether a customer has access to a certain account.
     * @param accountNumber The account to be tested
     * @param customerId The customer ID to be tested
     * @return boolean signifying whether the customer is authorized
     */
    private boolean getCustomerAuthorization(final String accountNumber, final String customerId) {
        try {
            String effectiveAccountNumber;
            if (accountNumber.endsWith("S")) {
                effectiveAccountNumber = accountNumber.substring(0, accountNumber.length() - 1);
            } else {
                effectiveAccountNumber = accountNumber;
            }
            // TODO THIS SERVICE IS NOT ALLOWED TO USE THE TABLES FROM THE USERS SERVICE
            SQLConnection databaseConnection = db.getConnection();
            PreparedStatement getAccountNumbers = databaseConnection.getConnection()
                    .prepareStatement(SQLStatements.getAccountNumbers);
            getAccountNumbers.setString(1, customerId);
            ResultSet accountRows = getAccountNumbers.executeQuery();
            boolean authorized = false;
            while (accountRows.next() && !authorized) {
                if (accountRows.getString("account_number").equals(effectiveAccountNumber)) {
                    authorized = true;
                }
            }
            accountRows.close();
            getAccountNumbers.close();
            db.returnConnection(databaseConnection);
            return authorized;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Receives a request to process a datarequest.
     * The datarequest is either for account information, a transaction history, or to check if an account exists.
     * @param callback Used to send a reply to the request source.
     * @param data JSON String representing a DataRequest containing the request information
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void dataRequestListener(final Callback<String> callback, final @RequestParam("data") String data) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        DataRequest dataRequest = (DataRequest) messageWrapper.getData();
        RequestType requestType = dataRequest.getType();
        System.out.printf("%s Received data request of type %s.\n", PREFIX, dataRequest.getType().toString());
        if (requestType != RequestType.ACCOUNTEXISTS
                && !messageWrapper.isAdmin()
                && !getCustomerAuthorization(dataRequest.getAccountNumber(), "" + dataRequest.getCustomerId())) {
            System.out.printf("%s rejecting because not authorized\n", PREFIX);
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.",
                    "Customer not authorized to request data for this accountNumber.")));
        } else if (requestType == RequestType.BALANCE) {
            fetchCreditCardBalance(dataRequest, CallbackBuilder.newCallbackBuilder().withStringCallback(callback));
        } else {
            // Method call
            DataReply dataReply = processDataRequest(dataRequest);

            if (dataReply != null) {
                System.out.printf("%s Data request successful, sending callback.\n", PREFIX);
                callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                        false, 200, "Normal Reply", dataReply)));
            } else {
                System.out.printf("%s Data request failed, sending rejection.\n", PREFIX);
                callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                        true, 500, "Error connecting to the Ledger database.")));
            }
        }
    }

    /**
     * Processes a datarequest.
     * The datarequest is either for account information, a transaction history, or to check if an account exists.
     * @param dataRequest Object representing a DataRequest containing the request information
     * @return the dataReply, or null if it failed
     */
    DataReply processDataRequest(final DataRequest dataRequest) {
        try {
            switch (dataRequest.getType()) {
                case TRANSACTIONHISTORY:
                    return processTransactionHistoryRequest(dataRequest);
                case ACCOUNTEXISTS:
                    return processAccountExistsRequest(dataRequest);
                default:
                    return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void fetchCreditCardBalance(final DataRequest dataRequest, final CallbackBuilder callbackBuilder) {
        pinClient.getAsyncWith1Param("/services/pin/creditCard/balance", "accountNumber",
                dataRequest.getAccountNumber(), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyJson),
                                MessageWrapper.class);
                        try {
                            if (messageWrapper.isError()) {
                                if (messageWrapper.getCode() == 418) {
                                    // no credit card for this account Number
                                    sendBalanceRequestCallback(processBalanceRequest(dataRequest,
                                            null), callbackBuilder);
                                } else {
                                    callbackBuilder.build().reply(replyJson);
                                }
                            } else {
                                Double creditCardBalance = (Double) messageWrapper.getData();
                                sendBalanceRequestCallback(processBalanceRequest(dataRequest,
                                        creditCardBalance), callbackBuilder);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    /**
     * Process a data request for the balance of an account.
     * @param dataRequest Object representing a DataRequest containing the request information
     * @return the dataReply
     * @throws SQLException sql Exception
     */
    private DataReply processBalanceRequest(final DataRequest dataRequest, final Double creditCardBalance)
                                            throws SQLException {
        SQLConnection connection = db.getConnection();
        DataReply dataReply = null;
        PreparedStatement ps = connection.getConnection().prepareStatement(getAccountInformation);
        ps.setString(1, dataRequest.getAccountNumber());     // account_number
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String accountNumber = dataRequest.getAccountNumber();
            String name = rs.getString("name");
            double overdraftLimit = rs.getDouble("overdraft_limit");
            double balance = rs.getDouble("balance");
            boolean savingsActive = rs.getBoolean("savings_active");
            double savingsBalance = rs.getDouble("savings_balance");
            Account account;
            if (creditCardBalance != null) {
                account = new Account(name, overdraftLimit, balance, savingsActive, savingsBalance, creditCardBalance);
            } else {
                account = new Account(name, overdraftLimit, balance, savingsActive, savingsBalance);
            }
            account.setAccountNumber(accountNumber);
            dataReply = JSONParser.createJsonDataReply(dataRequest.getAccountNumber(), dataRequest.getType(), account);
        }

        rs.close();
        ps.close();
        db.returnConnection(connection);

        return dataReply;
    }

    private void sendBalanceRequestCallback(final DataReply dataReply, final CallbackBuilder callbackBuilder) {
        if (dataReply != null) {
            System.out.printf("%s Data request successful, sending callback.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    false, 200, "Normal Reply", dataReply)));
        } else {
            System.out.printf("%s Data request failed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to the Ledger database.")));
        }
    }

    /**
     * Process a data request for the transaction history of an account.
     * @param dataRequest Object representing a DataRequest containing the request information
     * @return the dataReply
     * @throws SQLException sql Exception
     */
    private DataReply processTransactionHistoryRequest(final DataRequest dataRequest) throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement ps1 = connection.getConnection().prepareStatement(getIncomingTransactionHistory);
        PreparedStatement ps2 = connection.getConnection().prepareStatement(getOutgoingTransactionHistory);
        ps1.setString(1, dataRequest.getAccountNumber());     // account_number
        ps2.setString(1, dataRequest.getAccountNumber());     // account_number
        ResultSet rs1 = ps1.executeQuery();
        ResultSet rs2 = ps2.executeQuery();

        LinkedList<Transaction> transactions = new LinkedList<>();
        fillTransactionList(transactions, rs1);
        fillTransactionList(transactions, rs2);

        rs1.close();
        rs2.close();
        ps1.close();
        ps2.close();
        db.returnConnection(connection);

        return JSONParser.createJsonDataReply(dataRequest.getAccountNumber(), dataRequest.getType(), transactions);
    }

    /**
     * Process a data request for the existence of an account.
     * @param dataRequest Object representing a DataRequest containing the request information
     * @return the dataReply
     * @throws SQLException sql Exception
     */
    private DataReply processAccountExistsRequest(final DataRequest dataRequest) throws SQLException {
        SQLConnection connection = db.getConnection();
        boolean accountExists = false;
        PreparedStatement getAccountNumberCount = connection.getConnection()
                .prepareStatement(SQLStatements.getAccountNumberCount);
        getAccountNumberCount.setString(1, dataRequest.getAccountNumber());
        ResultSet accountNumberCount = getAccountNumberCount.executeQuery();

        if (accountNumberCount.next()) {
            int accountCount = accountNumberCount.getInt(1);
            if (accountCount > 0) {
                accountExists = true;
            }
        }

        accountNumberCount.close();
        db.returnConnection(connection);

        return JSONParser.createJsonDataReply(dataRequest.getAccountNumber(), dataRequest.getType(), accountExists);
    }

    /**
     * Fills a provided list with transactions from a given database query.
     * @param list The list to add the transactions to.
     * @param rs The ResultSet to get the transactions from.
     * @throws SQLException SQLException
     */
    private void fillTransactionList(final List<Transaction> list, final ResultSet rs) throws SQLException {
        while (rs.next()) {
            long id = rs.getLong("id");
            LocalDate date = rs.getDate("date").toLocalDate();
            String sourceAccount = rs.getString("account_from");
            String destinationAccount = rs.getString("account_to");
            String destinationAccountHolderName = rs.getString("account_to_name");
            String description = rs.getString("description");
            double amount = rs.getDouble("amount");
            double newBalance = rs.getDouble("new_balance");

            list.add(new Transaction(id, date, sourceAccount, destinationAccount, destinationAccountHolderName,
                    description, amount, newBalance));
        }
    }

    /**
     * Receives a request to process a month of interest.
     * @param callback Used to send a reply to the request source.
     * @param body JSON String representing a LocalDate
     */
    @RequestMapping(value = "/interest", method = RequestMethod.POST)
    public void incomingInterestRequestListener(final Callback<String> callback,
            final @RequestParam("request") String body) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        LocalDate localDate = jsonConverter.fromJson(body, LocalDate.class);
        System.out.printf("%s Received an interest processing request for date: %s\n", PREFIX, localDate.toString());
        processInterestRequest(localDate, callbackBuilder);
    }

    /**
     * Processes an interest request.
     * @param localDate Object representing a LocalDate
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void processInterestRequest(final LocalDate localDate, final CallbackBuilder callbackBuilder) {
        try {
            //date is the first day of the new month, so process the previous month.
            LocalDate firstProcessDay = localDate.minusMonths(1);
            LocalDate lastProcessDay = localDate.minusDays(1);
            List<String> overdraftAccounts = findOverdraftAccounts(firstProcessDay, lastProcessDay);
            Map<String, Double> overdraftInterestMap = calculateOverdraftInterest(overdraftAccounts, firstProcessDay,
                                                                        lastProcessDay);
            withdrawOverdraftInterest(overdraftInterestMap, localDate);
            // If jan 1 do savings interest processing for last year.
            if (localDate.getDayOfYear() == 1) {
                firstProcessDay = localDate.minusYears(1);
                lastProcessDay = localDate.minusDays(1);
                List<String> savingsAccounts = findSavingsAccounts(firstProcessDay, lastProcessDay);
                Map<String, Double> savingsInterestMap = calculateSavingsInterest(savingsAccounts, firstProcessDay,
                                                                                    lastProcessDay);
                depositSavingsInterest(savingsInterestMap, localDate);
            }
            sendInterestCallback(localDate, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                                                "Error connecting to the Ledger database.")));
        }
        sendInterestCallback(localDate, callbackBuilder);
    }

    /**
     * Finds a list of all accountNumbers that went overdraft in a given time period.
     * @param firstProcessDay First day of the time period.
     * @param lastProcessDay Last day of the time period.
     * @return List of all account numbers that went overdraft.
     * @throws SQLException Thrown when something goes wrong with the database connection.
     */
    private List<String> findOverdraftAccounts(final LocalDate firstProcessDay, final LocalDate lastProcessDay)
            throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement getOverdraftAccounts = connection.getConnection()
                .prepareStatement(SQLStatements.getOverdraftAccounts);
        getOverdraftAccounts.setDate(1, java.sql.Date.valueOf(firstProcessDay));
        getOverdraftAccounts.setDate(2, java.sql.Date.valueOf(lastProcessDay));
        getOverdraftAccounts.setDate(3, java.sql.Date.valueOf(firstProcessDay));
        getOverdraftAccounts.setDate(4, java.sql.Date.valueOf(lastProcessDay));
        ResultSet overdraftAccountSet = getOverdraftAccounts.executeQuery();
        List<String> overdraftAccounts = new LinkedList<>();
        while (overdraftAccountSet.next()) {
            overdraftAccounts.add(overdraftAccountSet.getString(1));
        }
        getOverdraftAccounts.close();
        db.returnConnection(connection);
        return overdraftAccounts;
    }

    private List<String> findSavingsAccounts(final LocalDate firstProcessDay, final LocalDate lastProcessDay)
            throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement getSavingsAccounts = connection.getConnection()
                .prepareStatement(SQLStatements.getSavingsAccounts);
        getSavingsAccounts.setDate(1, java.sql.Date.valueOf(firstProcessDay));
        getSavingsAccounts.setDate(2, java.sql.Date.valueOf(lastProcessDay));
        getSavingsAccounts.setDate(3, java.sql.Date.valueOf(firstProcessDay));
        getSavingsAccounts.setDate(4, java.sql.Date.valueOf(lastProcessDay));
        ResultSet savingsAccountSet = getSavingsAccounts.executeQuery();
        List<String> savingsAccounts = new LinkedList<>();
        while (savingsAccountSet.next()) {
            String account = savingsAccountSet.getString(1);
            if (account.endsWith("S")) {
                account = account.substring(0, account.length() - 1);
            }
            savingsAccounts.add(account);
        }
        getSavingsAccounts.close();
        db.returnConnection(connection);
        return savingsAccounts;
    }

    /**
     * Calculates the interest for a list of accounts that went overdraft, for a given time period.
     * Time period MUST be smaller than one year.
     * @param overdraftAccounts List of accounts that went overdraft in the given time period
     * @param firstProcessDay First day of time period.
     * @param lastProcessDay Last day of time period.
     * @return Map containing accountNumbers as keys, and their respective interest as values.
     * @throws SQLException Thrown when something goes wrong when connecting to the database.
     */
    private Map<String, Double> calculateOverdraftInterest(final List<String> overdraftAccounts,
                                                           final LocalDate firstProcessDay,
                                                           final LocalDate lastProcessDay) throws SQLException {
        Map<String, Double> interestMap = new HashMap<>();
        double dailyInterestRate = MONTHLY_OVERDRAFT_RATE / firstProcessDay.getMonth()
                                                                            .length(firstProcessDay.isLeapYear());
        for (String accountNumber : overdraftAccounts) {
            List<Transaction> overdraftTransactions = findOverdraftTransactions(accountNumber, firstProcessDay,
                                                                         lastProcessDay);
            if (overdraftTransactions.isEmpty()) {
                Account accountInfo = getAccountInfo(accountNumber);
                Double balance = accountInfo.getBalance();
                if (balance < 0) {
                    int amountOfDays = lastProcessDay.getDayOfYear() - firstProcessDay.getDayOfYear();
                    Double interest = amountOfDays * dailyInterestRate * (-1 * balance);
                    interestMap.put(accountNumber, interest);
                }
            } else {
                interestMap.put(accountNumber, doDailyOverdraftInterestCalculation(accountNumber, overdraftTransactions,
                                                                          firstProcessDay, lastProcessDay,
                                                                          dailyInterestRate));
            }
        }
        return interestMap;
    }

    private Map<String, Double> calculateSavingsInterest(final List<String> savingsAccounts,
                                                         final LocalDate firstProcessDay,
                                                         final LocalDate lastProcessDay) throws SQLException {
        Map<String, Double> interestMap = new HashMap<>();
        for (String accountNumber : savingsAccounts) {
            List<Transaction> savingsTransactions = findSavingsTransactions(accountNumber, firstProcessDay,
                                                                            lastProcessDay);
            if (savingsTransactions.isEmpty()) {
                Account accountInfo = getAccountInfo(accountNumber);
                Double savingsBalance = accountInfo.getSavingsBalance();
                if (savingsBalance > 0) {
                    interestMap.put(accountNumber, calculateSavingsInterest(savingsBalance));
                }
            } else {
                interestMap.put(accountNumber, doDailySavingsInterestCalculation(accountNumber, savingsTransactions,
                                                                                 firstProcessDay, lastProcessDay));
            }
        }
        return  interestMap;
    }

    private Double calculateSavingsInterest(final Double averageBalance) {
        Double interest;
        if (averageBalance > TIER_1_CAP) {
            interest = TIER_1_CAP * TIER_1_INTEREST_RATE;
            if (averageBalance > TIER_2_CAP) {
                interest += (TIER_2_CAP - TIER_1_CAP) * TIER_2_INTEREST_RATE;
                interest += (averageBalance - TIER_2_CAP) * TIER_3_INTEREST_RATE;
            } else {
                interest += (averageBalance - TIER_1_CAP) * TIER_2_INTEREST_RATE;
            }
        } else {
            interest = averageBalance * TIER_1_INTEREST_RATE;
        }
        return interest;
    }

    /**
     * Calculates the interest for an account separately for each day based on the lowest balance of that day.
     * Returns the interest owed for the given time period of an overdraft account.
     * @param accountNumber AccountNumber to calculate the interest for.
     * @param overdraftTransactions List of transactions that affected the overdraft of the account during the
     *                              time period.
     * @param firstProcessDay First day of the time period.
     * @param lastProcessDay Last day of the time period.
     * @param dailyInterestRate Interest rate for a single day.
     * @return The interest this account owes for the given time period.
     */
    private Double doDailyOverdraftInterestCalculation(final String accountNumber,
                                                       final List<Transaction> overdraftTransactions,
                                                       final LocalDate firstProcessDay, final LocalDate lastProcessDay,
                                                       final Double dailyInterestRate) {
        Double interest = 0.0;
        LocalDate currentProcessDay = firstProcessDay;
        Double currentBalance;
        // sort transactions from earliest to latest.
        overdraftTransactions.sort(Comparator.comparing(Transaction::getDate));
        Transaction firstTransactionOfMonth = overdraftTransactions.get(0);
        if (firstTransactionOfMonth.getSourceAccountNumber().equals(accountNumber)) {
            currentBalance = firstTransactionOfMonth.getNewBalance()
                    + firstTransactionOfMonth.getTransactionAmount();
        } else {
            currentBalance = firstTransactionOfMonth.getNewBalance()
                    - firstTransactionOfMonth.getTransactionAmount();
        }
        while (currentProcessDay.isBefore(lastProcessDay) || currentProcessDay.isEqual(lastProcessDay)) {
            // process transactions of this day and find the lowest balance.
            Double lowestBalance = currentBalance;
            while (!overdraftTransactions.isEmpty()
                        && overdraftTransactions.get(0).getDate().equals(currentProcessDay)) {
                Transaction transactionToProcess = overdraftTransactions.remove(0);
                currentBalance = transactionToProcess.getNewBalance();
                if (currentBalance < lowestBalance) {
                    lowestBalance = currentBalance;
                }
            }
            if (lowestBalance < 0) {
                interest += dailyInterestRate * (lowestBalance * -1);
            }
            currentProcessDay = currentProcessDay.plusDays(1);
        }
        return interest;
    }

    private Double doDailySavingsInterestCalculation(final String accountNumber,
                                                     final List<Transaction> savingsTransactions,
                                                     final LocalDate firstProcessDay,
                                                     final LocalDate lastProcessDay) {
        Double averageBalance = 0.0;
        LocalDate currentProcessDay = firstProcessDay;
        Double currentBalance;
        // sort transactions from earliest to latest.
        savingsTransactions.sort(Comparator.comparing(Transaction::getDate));
        Transaction firstTransaction = savingsTransactions.get(0);
        if (firstTransaction.getSourceAccountNumber().equals(accountNumber + "S")) {
            currentBalance = firstTransaction.getNewSavingsBalance()
                    + firstTransaction.getTransactionAmount();
        } else {
            currentBalance = firstTransaction.getNewSavingsBalance()
                    - firstTransaction.getTransactionAmount();
        }
        while (currentProcessDay.isBefore(lastProcessDay) || currentProcessDay.isEqual(lastProcessDay)) {
            // process transactions of this day and find the lowest balance.
            Double lowestBalance = currentBalance;
            while (!savingsTransactions.isEmpty()
                    && savingsTransactions.get(0).getDate().equals(currentProcessDay)) {
                Transaction transactionToProcess = savingsTransactions.remove(0);
                currentBalance = transactionToProcess.getNewSavingsBalance();
                if (currentBalance < lowestBalance) {
                    lowestBalance = currentBalance;
                }
            }
            averageBalance += (1.00 / (lastProcessDay.getDayOfYear() - firstProcessDay.getDayOfYear() + 1)) * (lowestBalance);
            currentProcessDay = currentProcessDay.plusDays(1);
        }
        return calculateSavingsInterest(averageBalance);
    }

    /**
     * Find transactions that caused an account to go overdraft or changed the overdraft balance of an account during
     * a given time period.
     * @param accountNumber AccountNumber to find transactions for.
     * @param firstProcessDay First day of the time period.
     * @param lastProcessDay Last day of the time period.
     * @return List of transactions that affected the overdraft of an account.
     * @throws SQLException Thrown when something goes wrong connecting to the database.
     */
    private List<Transaction> findOverdraftTransactions(final String accountNumber,
                                                             final LocalDate firstProcessDay,
                                                             final LocalDate lastProcessDay) throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement getOverdraftTransactions = connection.getConnection()
                                                      .prepareStatement(SQLStatements.getAccountOverdraftTransactions);
        getOverdraftTransactions.setString(1, accountNumber);
        getOverdraftTransactions.setDate(2, java.sql.Date.valueOf(firstProcessDay));
        getOverdraftTransactions.setDate(3, java.sql.Date.valueOf(lastProcessDay));
        getOverdraftTransactions.setString(4, accountNumber);
        getOverdraftTransactions.setDate(5, java.sql.Date.valueOf(firstProcessDay));
        getOverdraftTransactions.setDate(6, java.sql.Date.valueOf(lastProcessDay));
        ResultSet overdraftTransactionSet = getOverdraftTransactions.executeQuery();
        List<Transaction> overdraftTransactions = new LinkedList<>();
        while (overdraftTransactionSet.next()) {
            Long transactionId = overdraftTransactionSet.getLong("id");
            LocalDate transactionDate = overdraftTransactionSet.getDate("date").toLocalDate();
            String accountTo = overdraftTransactionSet.getString("account_to");
            String accountToName = overdraftTransactionSet.getString("account_to_name");
            String accountFrom = overdraftTransactionSet.getString("account_from");
            Double amount = overdraftTransactionSet.getDouble("amount");
            Double newBalance = overdraftTransactionSet.getDouble("new_balance");
            String description = overdraftTransactionSet.getString("description");
            Transaction transaction = new Transaction(transactionId, transactionDate, accountFrom, accountTo,
                                                      accountToName, description, amount, newBalance);
            overdraftTransactions.add(transaction);
        }
        getOverdraftTransactions.close();
        db.returnConnection(connection);
        return overdraftTransactions;
    }

    private List<Transaction> findSavingsTransactions(final String accountNumber,
                                                      final LocalDate firstProcessDay,
                                                      final LocalDate lastProcessDay) throws SQLException {
        SQLConnection connection = db.getConnection();
        PreparedStatement getSavingsTransactions = connection.getConnection()
                                                        .prepareStatement(SQLStatements.getAccountSavingsTransactions);
        getSavingsTransactions.setString(1, accountNumber);
        getSavingsTransactions.setDate(2, java.sql.Date.valueOf(firstProcessDay));
        getSavingsTransactions.setDate(3, java.sql.Date.valueOf(lastProcessDay));
        getSavingsTransactions.setString(4, accountNumber);
        getSavingsTransactions.setDate(5, java.sql.Date.valueOf(firstProcessDay));
        getSavingsTransactions.setDate(6, java.sql.Date.valueOf(lastProcessDay));
        ResultSet savingsTransactionSet = getSavingsTransactions.executeQuery();
        List<Transaction> savingsTransactions = new LinkedList<>();
        while (savingsTransactionSet.next()) {
            Long transactionId = savingsTransactionSet.getLong("id");
            LocalDate transactionDate = savingsTransactionSet.getDate("date").toLocalDate();
            String accountTo = savingsTransactionSet.getString("account_to");
            String accountToName = savingsTransactionSet.getString("account_to_name");
            String accountFrom = savingsTransactionSet.getString("account_from");
            Double amount = savingsTransactionSet.getDouble("amount");
            Double newBalance = savingsTransactionSet.getDouble("new_balance");
            String description = savingsTransactionSet.getString("description");
            Transaction transaction = new Transaction(transactionId, transactionDate, accountFrom, accountTo,
                    accountToName, description, amount, newBalance);
            Double newSavingsBalance = savingsTransactionSet.getDouble("new_savings_balance");
            transaction.setNewSavingsBalance(newSavingsBalance);
            savingsTransactions.add(transaction);
        }
        getSavingsTransactions.close();
        db.returnConnection(connection);
        return savingsTransactions;
    }

    /**
     * Processes interest withdrawals in the system.
     * @param interestMap Map containing accountNumbers as keys, and their owed interest as values.
     * @param currentDate Date during the interest withdrawal.
     */
    private void withdrawOverdraftInterest(final Map<String, Double> interestMap, final LocalDate currentDate) {
        final String firstDayOfInterest = currentDate.minusMonths(1).toString();
        final String lastDayOfInterest = currentDate.minusDays(1).toString();
        for (String accountNumber : interestMap.keySet()) {
            Transaction transaction = new Transaction();
            transaction.setSourceAccountNumber(accountNumber);
            transaction.setTransactionAmount(interestMap.get(accountNumber));
            transaction.setDestinationAccountNumber(GNI_ACCOUNT);
            transaction.setDestinationAccountHolderName("GNI Bank");
            transaction.setDescription(String.format("Overdraft interest %s until %s", firstDayOfInterest,
                                                    lastDayOfInterest));
            Account account = getAccountInfo(accountNumber);
            // Update the object
            account.processWithdraw(transaction);

            // Update the database
            updateBalance(account);
            transaction.setNewBalance(account.getBalance());

            /// Update Transaction log
            transaction.setTransactionID(getHighestTransactionID());
            transaction.setDate(currentDate);
            addTransaction(transaction, false);
        }
    }

    private void depositSavingsInterest(final Map<String, Double> interestMap, final LocalDate currentDate) {
        final String firstDayOfInterest = currentDate.minusMonths(1).toString();
        final String lastDayOfInterest = currentDate.minusDays(1).toString();
        for (String accountNumber : interestMap.keySet()) {
            Transaction transaction = new Transaction();
            transaction.setSourceAccountNumber(GNI_ACCOUNT);
            transaction.setTransactionAmount(interestMap.get(accountNumber));
            transaction.setDestinationAccountNumber(accountNumber);
            transaction.setDestinationAccountHolderName("GNI Bank");
            transaction.setDescription(String.format("Savings interest %s until %s", firstDayOfInterest,
                    lastDayOfInterest));
            Account account = getAccountInfo(accountNumber);
            // Update the object
            account.processSavingsInterest(transaction);

            // Update the database
            updateSavingsBalance(account);
            transaction.setNewSavingsBalance(account.getSavingsBalance());

            /// Update Transaction log
            transaction.setTransactionID(getHighestTransactionID());
            transaction.setDate(currentDate);
            addTransaction(transaction, true);
        }
    }

    /**
     * Sends a callback back to the source of the request.
     * @param localDate Object representing a LocalDate
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void sendInterestCallback(final LocalDate localDate, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully processed interest for date: %s, sending callback.\n",
                PREFIX, localDate.toString());
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply")));
    }

    /**
     * Receives a request to process a new overdraft limit.
     * @param accountNumber AccountNumber of which the limit should be set.
     * @param overdraftLimit New overdraft limit
     * @param callback Used to send a reply to the request source.
     */
    @RequestMapping(value = "/overdraft/set", method = RequestMethod.PUT)
    public void incomingSetOverdraftLimitRequestListener(final Callback<String> callback,
                                                final @RequestParam("accountNumber") String accountNumber,
                                                final @RequestParam("overdraftLimit") Double overdraftLimit) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.printf("%s Received a setOverdraftLimit request for accountNumber: %s\n", PREFIX, accountNumber);
        handleSetOverdraftLimitExceptions(accountNumber, overdraftLimit, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the request with the accountNumber to ledger.
     * @param accountNumber AccountNumber of which the limit should be set.
     * @param overdraftLimit New overdraft limit
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleSetOverdraftLimitExceptions(
            final String accountNumber, final Double overdraftLimit, final CallbackBuilder callbackBuilder) {
        try {
            Account account = getAccountInfo(accountNumber);
            if (account != null) {
                account.setOverdraftLimit(overdraftLimit);
                updateOverdraftLimit(account);
                sendSetOverdraftLimitCallback(callbackBuilder);
            } else {
                throw new SQLException("Account does not exist.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the ledger database.")));
        }
    }

    /**
     * Sends a callback back to the source of the request.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void sendSetOverdraftLimitCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully processed setOverdraftLimit request, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply")));
    }

    /**
     * Receives a request to query an overdraft limit.
     * @param data AccountNumber of which the limit should be queried. (& admin info)
     * @param callback Used to send a reply to the request source.
     */
    @RequestMapping(value = "/overdraft/get", method = RequestMethod.PUT)
    public void incomingGetOverdraftLimitRequestListener(final Callback<String> callback,
                                                final @RequestParam("data") String data) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        System.out.printf("%s Received a getOverdraftLimit request for accountNumber: %s\n",
                PREFIX, ((DataRequest) messageWrapper.getData()).getAccountNumber());
        handleGetOverdraftLimitExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the request with the accountNumber to ledger.
     * @param messageWrapper AccountNumber of which the limit should be queried. (& admin info)
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleGetOverdraftLimitExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        DataRequest dataRequest = (DataRequest) messageWrapper.getData();
        if (messageWrapper.isAdmin()
                || getCustomerAuthorization(dataRequest.getAccountNumber(), "" + dataRequest.getCustomerId())) {
            Account account = getAccountInfo(dataRequest.getAccountNumber());
            if (account != null) {
                Integer overdraftLimit = (int) account.getOverdraftLimit();
                sendGetOverdraftLimitCallback(overdraftLimit, callbackBuilder);
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                        "One of the parameters has an invalid value.",
                        "The requested account does not appear to exist. (or database failure)")));
            }
        } else {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.")));
        }
    }

    /**
     * Sends a callback back to the source of the request.
     * @param overdraftLimit The queried overdraftLimit
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void sendGetOverdraftLimitCallback(final Integer overdraftLimit, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully processed getOverdraftLimit request, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", overdraftLimit)));
    }

    /**
     * Activates the savings account linked to an already existing iBAN account.
     * @param callback Used to send a reply to the request source.
     * @param iBAN AccountNumber of the account to activate the savings account for.
     */
    @RequestMapping(value = "/savingsAccount", method = RequestMethod.PUT)
    public void openSavingsAccount(final Callback<String> callback, @RequestParam("iBAN") final String iBAN) {
        System.out.printf("%s Received open savings account request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        updateSavingsStatus(true, iBAN);
        sendOpenSavingsAccountCallback(callbackBuilder);
    }

    /**
     * Updates the status of a savings account.
     * @param isActive Status to update it to.
     * @param iBAN AccountNumber the savings account is linked to.
     */
    void updateSavingsStatus(final boolean isActive, final String iBAN) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement addSavingsAccountToDb = connection.getConnection()
                    .prepareStatement(SQLStatements.updateSavingsStatus);
            addSavingsAccountToDb.setBoolean(1, isActive);
            addSavingsAccountToDb.setString(2, iBAN);
            addSavingsAccountToDb.execute();
            addSavingsAccountToDb.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a callback for the open savings account request indicating the request was successful.
     * @param callbackBuilder Used to send the response to the request source.
     */
    private void sendOpenSavingsAccountCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Open savings account request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply")));
    }

    /**
     * Closes a savings account for a given account, if there were any funds left in the savings account these will be
     * transferred to the normal account.
     * @param callback Used to send a reply to the request source.
     * @param iBAN AccountNumber of the savings account to close(same as the regular account).
     */
    @RequestMapping(value = "/savingsAccount/close", method = RequestMethod.PUT)
    public void closeSavingsAccount(final Callback<String> callback, @RequestParam("iBAN") final String iBAN) {
        System.out.printf("%s Received close savings account request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleCloseSavingsAccountExceptions(iBAN, callbackBuilder);
    }

    /**
     * If an sql exception occurs this method will send a callback indicating that an error occurred.
     * @param iBAN AccountNumber of the account for which the savings account should be closed.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void handleCloseSavingsAccountExceptions(final String iBAN, final CallbackBuilder callbackBuilder) {
        try {
            deactivateSavingsAccount(iBAN, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the Ledger database.")));
        }
    }

    /**
     * Deactivates a savings account in the database. If there are any funds left in the savings account these will be
     * transferred to the regular account.
     * @param iBAN AccountNumber of the account that owns the savings account.
     * @param callbackBuilder Used to send a reply to the request source.
     * @throws SQLException Thrown when the database connection fails.
     */
    private void deactivateSavingsAccount(final String iBAN, final CallbackBuilder callbackBuilder)
            throws SQLException {
        Account account = getAccountInfo(iBAN);
        if (account != null) {
            Double savingsBalance = account.getSavingsBalance();
            if (savingsBalance > 0) {
                // Update the object before async call
                account.transferSavingsToMain();
                // fetch date
                systemInformationClient.getAsync("/services/systemInfo/date", (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(body),
                                MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            LocalDate date = (LocalDate) messageWrapper.getData();
                            // Update the database
                            updateBalance(account);
                            updateSavingsBalance(account);
                            updateSavingsStatus(false, account.getAccountNumber());
                            // Create transaction for transaction history
                            Transaction transaction = new Transaction(getHighestTransactionID(),
                                    account.getAccountNumber() + "S", account.getAccountNumber(),
                                    account.getAccountHolderName(),
                                    "Transfer of savings account to main account.", savingsBalance);
                            transaction.setNewBalance(account.getBalance());
                            transaction.setNewSavingsBalance(account.getSavingsBalance());
                            transaction.setDate(date);
                            addTransaction(transaction, true);
                            sendCloseSavingsAccountCallback(callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        System.out.printf("%s Processing removal of savings account failed, body: %s\n\n\n\n",
                                PREFIX, body);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                                500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
            } else {
                updateSavingsStatus(false, account.getAccountNumber());
                sendCloseSavingsAccountCallback(callbackBuilder);
            }
        } else {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                    418, "One of the parameters has an invalid value.",
                    "There is no account in the system with that iBAN.")));
        }
    }

    /**
     * Overwrites account information for a specific account,
     * in case a transaction is successfully processed.
     * @param account The account to overwrite, containing the new data
     */
    void updateSavingsBalance(final Account account) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateSavingsBalance);
            ps.setDouble(1, account.getSavingsBalance());
            ps.setString(2, account.getAccountNumber());
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a successful callback for a close savings account request.
     * @param callbackBuilder Used to send a reply to the request source.
     */
    private void sendCloseSavingsAccountCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Close savings account request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply")));
    }

    /**
     * Safely shuts down the LedgerService.
     */
    void shutdown() {
        if (db != null) db.close();
    }
}
