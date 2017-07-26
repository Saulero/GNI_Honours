package users;

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
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static database.SQLStatements.*;
import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 2
 * The Users Microservice, handles customer information for the system.
 */
@RequestMapping("/users")
class UsersService {
    /** Connection to the Ledger service.*/
    private HttpClient ledgerClient;
    /** Connection to the Transaction Dispatch service.*/
    private HttpClient transactionDispatchClient;
    /** Connection pool with database connections for the User Service. */
    private ConnectionPool databaseConnectionPool;
    /** Gson object used to convert objects to/from json. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Users Service. */
    private static final String PREFIX = "[Users]               :";

    /**
     * Constructor.
     * @param ledgerPort Port the LedgerService service can be found on.
     * @param ledgerHost Host the LedgerService service can be found on.
     * @param transactionDispatchPort Port the TransactionDispatch service can be found on.
     * @param transactionDispatchHost Host the TransactionDispatch service can be found on.
     */
    UsersService(final int ledgerPort, final String ledgerHost, final int transactionDispatchPort,
                 final String transactionDispatchHost) {
        ledgerClient = httpClientBuilder().setHost(ledgerHost).setPort(ledgerPort).buildAndStart();
        transactionDispatchClient = httpClientBuilder().setHost(transactionDispatchHost)
                                                       .setPort(transactionDispatchPort).buildAndStart();
        this.databaseConnectionPool = new ConnectionPool();
        jsonConverter = new Gson();
    }

    /**
     * Minimal constructor for testing purposes.
     */
    UsersService() {
        this.databaseConnectionPool = new ConnectionPool();
        jsonConverter = new Gson();
    }

    /**
     * Checks if incoming data request needs to be handled internally of externally and then calls the appropriate
     * function.
     * @param callback Used to send a reply back to the service that sent the request.
     * @param dataRequestJson Json String representing a {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   final @RequestParam("request") String dataRequestJson) {
        DataRequest dataRequest = jsonConverter.fromJson(dataRequestJson, DataRequest.class);
        RequestType dataRequestType = dataRequest.getType();
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        if (dataRequestType == RequestType.CUSTOMERDATA
                || dataRequestType == RequestType.ACCOUNTS
                || dataRequestType == RequestType.ACCOUNTACCESSLIST) {
            handleInternalDataRequest(dataRequest, callbackBuilder);
        } else {
            doLedgerDataRequest(dataRequest, callbackBuilder);
        }
    }

    /**
     * Checks which type if internal data request needs to be processed and then calls the exception handler for the
     * respective type.
     * @param dataRequest Data request that needs to be handled.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleInternalDataRequest(final DataRequest dataRequest, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Received customer data request, fetching data.\n", PREFIX);
        switch (dataRequest.getType()) {
            case ACCOUNTS:
                handleAccountsRequestExceptions(dataRequest.getCustomerId(), callbackBuilder);
                break;
            case CUSTOMERDATA:
                handleCustomerDataRequestExceptions(dataRequest.getCustomerId(), callbackBuilder);
                break;
            case ACCOUNTACCESSLIST:
                handleAccountAccessListRequestExceptions(dataRequest.getAccountNumber(), dataRequest.getCustomerId(),
                        callbackBuilder);
                break;
            default:
                callbackBuilder.build().reject("Incorrect requestType specified.");
                break;
        }
    }

    /**
     * Sends a reject to the calling service if the SQL query in getCustomerAccounts fails.
     * @param customerId Id of the customer whose accounts we want to fetch.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleAccountsRequestExceptions(final long customerId, final CallbackBuilder callbackBuilder) {
        try {
            List<String> customerAccounts = getCustomerAccounts(customerId);
            forwardAccountsRequest(customerAccounts, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Send a DataReply object containing accounts belonging to a certain customer to the service that requested them.
     * @param customerAccounts DataReply object containing a list of accounts belonging to a certain customer.
     * @param callbackBuilder Used to send a reply back to the calling service.
     */
    private void sendAccountsRequestCallback(final DataReply customerAccounts, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending accounts request callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(customerAccounts));
    }

    /**
     * Fetches account numbers from the accounts table for the customer with the respective id, returns this in a
     * list object.
     * @param customerId Customer id of the customer we want to fetch accounts for.
     * @return List containing account numbers that belong to the customer.
     * @throws SQLException Indicates customer accounts could not be fetched.
     */
    List<String> getCustomerAccounts(final long customerId) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getAccountsFromDb = databaseConnection.getConnection().prepareStatement(getAccountNumbers);
        getAccountsFromDb.setLong(1, customerId);
        ResultSet retrievedAccounts = getAccountsFromDb.executeQuery();
        DataReply customerAccounts = new DataReply();
        customerAccounts.setType(RequestType.ACCOUNTS);
        List<String> linkedAccounts = new ArrayList<>();
        while (retrievedAccounts.next()) {
            linkedAccounts.add(retrievedAccounts.getString("account_number"));
        }
        getAccountsFromDb.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return  linkedAccounts;
    }

    private void forwardAccountsRequest(final List<String> accounts, final CallbackBuilder callbackBuilder) {
        DataRequest request = new DataRequest();
        request.setType(RequestType.OWNERS);
        request.setAccountNumbers(accounts);
        doLedgerDataRequest(request, callbackBuilder);
    }

    /**
     * Sends a reject to the service that sent the data request if the SQL query in getCustomerData fails or the
     * customer does not exist.
     * @param customerId Id of the customer to request data for.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleCustomerDataRequestExceptions(final long customerId, final CallbackBuilder callbackBuilder) {
        try {
            sendCustomerDataRequestCallback(getCustomerData(customerId), callbackBuilder);
        } catch (SQLException | CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Sends a {@link Customer} containing the customer data of a certain customer to the service that requested it.
     * Logs this is system.out.
     * @param customerData Customer information belonging to a certain customer
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendCustomerDataRequestCallback(final Customer customerData, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending customer data request callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(customerData));
    }

    /**
     * Fetches customer data from the customers table for a certain customer and returns this data in a {@link Customer}
     * object.
     * @param customerId Id of the customer to fetch data for.
     * @return Customer object containing the data for Customer with id=customerId
     * @throws SQLException Indicates customer data could not be fetched.
     * @throws CustomerDoesNotExistException Indicates there is no customer with that customer id.
     */
    Customer getCustomerData(final long customerId) throws SQLException, CustomerDoesNotExistException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCustomerDataFromDb = databaseConnection.getConnection()
                                                  .prepareStatement(getUserInformation);
        getCustomerDataFromDb.setLong(1, customerId);
        ResultSet retrievedCustomerData = getCustomerDataFromDb.executeQuery();
        Customer customerData = new Customer();
        if (retrievedCustomerData.next()) {
            customerData.setCustomerId(customerId);
            customerData.setInitials(retrievedCustomerData.getString("initials"));
            customerData.setName(retrievedCustomerData.getString("firstname"));
            customerData.setSurname(retrievedCustomerData.getString("lastname"));
            customerData.setEmail(retrievedCustomerData.getString("email"));
            customerData.setTelephoneNumber(retrievedCustomerData.getString("telephone_number"));
            customerData.setAddress(retrievedCustomerData.getString("address"));
            customerData.setDob(retrievedCustomerData.getString("date_of_birth"));
            customerData.setSsn(retrievedCustomerData.getLong("social_security_number"));
            getCustomerDataFromDb.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return customerData;
        } else {
            getCustomerDataFromDb.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            throw new CustomerDoesNotExistException("Customer not found in database.");
        }
    }

    /**
     * Sends a reject to the service that sent the data request if the SQL query in getCustomerData fails or the
     * customer does not exist.
     * @param accountNumber iBAN of the account
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleAccountAccessListRequestExceptions(final String accountNumber, final long customerID,
                                                          final CallbackBuilder callbackBuilder) {
        try {
            // TODO Check customerID against acountNumber, customer MUST BE primary owner
            DataReply reply = new DataReply(RequestType.ACCOUNTACCESSLIST, getAccountAccessList(accountNumber));
            sendAccountAccessListRequestCallback(reply, callbackBuilder);
        } catch (SQLException | AccountDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Sends a {@link Customer} containing the customer data of a certain customer to the service that requested it.
     * Logs this is system.out.
     * @param reply DataReply containing a list of customers who have access to the requested account.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendAccountAccessListRequestCallback(final DataReply reply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending account access list request callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(reply));
    }

    private List<AccountLink> getAccountAccessList(final String accountNumber)
            throws SQLException, AccountDoesNotExistException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection().prepareStatement(getAccountAccessList);
        ps.setString(1, accountNumber);
        ResultSet rs = ps.executeQuery();

        LinkedList<AccountLink> res = new LinkedList<>();
        while (rs.next()) {
            res.add(new AccountLink(rs.getLong("user_id")));
        }

        rs.close();
        ps.close();
        databaseConnectionPool.returnConnection(con);
        if (res.size() == 0) {
            throw new AccountDoesNotExistException("No AccountLinks were found.");
        } else {
            return res;
        }
    }

    /**
     * Forwards a data request to the ledger, sends a callback if the request succeeds, else rejects the callback.
     * @param dataRequest Data request that needs to be sent to the ledger.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doLedgerDataRequest(final DataRequest dataRequest, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Called for a data request, calling Ledger.\n", PREFIX);
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "request",
                                        jsonConverter.toJson(dataRequest),
                                        (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                sendLedgerDataRequestCallback(dataReplyJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Received an error from ledger.");
            }
        });
    }

    /**
     * Forwards a data response from the ledger to the service that requested it and then logs this to system.out.
     * @param dataReplyJson Json reply from the ledger representing a DataReply object.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendLedgerDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending data request callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
    }


    /**
     * Processes a transaction request by forwarding it to the TransactionDispatch service.
     * @param callback Used to send the result back to the calling service.
     * @param transactionRequestJson Json String containing a Transaction object representing a transaction request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransaction(final Callback<String> callback,
                                   final @RequestParam("request") String transactionRequestJson,
                                   final @RequestParam("customerId") String customerId) {
        Transaction transactionRequest = jsonConverter.fromJson(transactionRequestJson, Transaction.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.printf("%s Sending transaction to TransactionDispatch.\n", PREFIX);
        doTransactionRequest(transactionRequest, customerId, callbackBuilder);
    }

    /**
     * Sends transaction request to the TransactionDispatch service and processes the reply.
     * @param transactionRequest  {@link Transaction} request forwarded by the Authentication service.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doTransactionRequest(final Transaction transactionRequest, final String customerId,
                                      final CallbackBuilder callbackBuilder) {
        transactionDispatchClient.putFormAsyncWith2Params("/services/transactionDispatch/transaction",
                                                        "request", jsonConverter.toJson(transactionRequest),
                                                        "customerId", customerId,
                                                        (httpStatusCode, httpContentType, transactionReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                processTransactionReply(transactionReplyJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Couldn't reach transactionDispatch.");
            }
        });
    }

    /**
     * Checks if the transaction was processed and successful, and then invokes the corresponding callback.
     * @param transactionReplyJson Json String representing a Transaction resply that was received from the
     *                  TransactionDispatchService.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void processTransactionReply(final String transactionReplyJson, final CallbackBuilder callbackBuilder) {
        Transaction transactionReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(transactionReplyJson),
                                                              Transaction.class);
        if (transactionReply.isProcessed() && transactionReply.isSuccessful()) {
            sendTransactionRequestCallback(transactionReplyJson, callbackBuilder);
        } else {
            callbackBuilder.build().reject("Transaction failed, processed: "
                                            + transactionReply.isProcessed() + " successfull: "
                                            + transactionReply.isSuccessful());
        }
    }

    /**
     * Forwards a transaction reply from the transaction dispatch service to the service that requested the transaction
     * and then logs this to system.out.
     * @param transactionReplyJson Reply from the transaction dispatch service.
     * @param callbackBuilder Used to send a reply back to the calling service.
     */
    private void sendTransactionRequestCallback(final String transactionReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Transaction was successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(transactionReplyJson));
    }

    /**
     * Processes customer creation requests by creating a callback builder and then sending the Customer object to the
     * handler.
     * @param callback Used to send the result of the request back to the Authentication service.
     * @param customerRequestJson Json string containing the {@link Customer} the request is for.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomer(final Callback<String> callback,
                                   final @RequestParam("customer") String customerRequestJson) {
        Customer customerToEnroll = jsonConverter.fromJson(customerRequestJson, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewCustomerRequestExceptions(customerToEnroll, callbackBuilder);
    }

    /**
     * Sends a rejection to the service that requested the customer to be created if the SQL connection fails.
     * @param customerToEnroll Customer object containing the customer data of the customer that should be put in the
     *                         customers database.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleNewCustomerRequestExceptions(final Customer customerToEnroll,
                                                    final CallbackBuilder callbackBuilder) {
        try {
            customerToEnroll.setCustomerId(getNewCustomerId());
            enrollCustomer(customerToEnroll);
            doNewAccountRequest(customerToEnroll, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Fetches a customer Id for a new customer.
     * @return CustomerId to be assigned to a new customer.
     * @throws SQLException If the query fails the handler will reject the request.
     */
    long getNewCustomerId() throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        long newCustomerId = databaseConnection.getNextID(getNextUserID);
        databaseConnectionPool.returnConnection(databaseConnection);
        return newCustomerId;
    }

    /**
     * Enrolls a customer into the customers database.
     * @param customer Customer object containing the customers data to enroll in the database.
     * @throws SQLException Indicates that something went wrong when enrolling the user into the system, the handler
     *                      will then reject the new customer request.
     */
    //todo implement check to see if the customer already exists in the database.
    void enrollCustomer(final Customer customer) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement createNewCustomer = databaseConnection.getConnection().prepareStatement(createNewUser);
        createNewCustomer.setLong(1, customer.getCustomerId());        // id
        createNewCustomer.setString(2, customer.getInitials());        // initials
        createNewCustomer.setString(3, customer.getName());            // firstname
        createNewCustomer.setString(4, customer.getSurname());         // lastname
        createNewCustomer.setString(5, customer.getEmail());           // email
        createNewCustomer.setString(6, customer.getTelephoneNumber()); //telephone_number
        createNewCustomer.setString(7, customer.getAddress());         //address
        createNewCustomer.setString(8, customer.getDob());             //date_of_birth
        createNewCustomer.setLong(9, customer.getSsn());               //social_security_number
        createNewCustomer.executeUpdate();
        createNewCustomer.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        System.out.printf("%s New customer successfully enrolled.\n", PREFIX);
    }

    /**
     * Sends a request to the ledger asking for the ledger to create a new account, if the ledger sends back
     * an account number it sends that off for processing, if the ledger call fails it sends a rejection to the
     * service that sent the request to this service.
     * @param accountOwner Customer object representing the owner of the account to be created, should also contain an
     *                     account object with a specified accountHolderName, balance and spendingLimit.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doNewAccountRequest(final Customer accountOwner, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/account", "body",
                                            jsonConverter.toJson(accountOwner.getAccount()),
                                            (httpStatusCode, httpContentType, replyAccountJson) -> {
            if (httpStatusCode == HTTP_OK) {
                processNewAccountReply(replyAccountJson, accountOwner, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Received an error from ledger.");
            }
        });
    }

    /**
     * Processes a reply from the ledger containing a new account which is to be linked to a customer.
     * @param replyAccountJson Json String representing an {@link Account} that should be linked to a {@link Customer}.
     * @param accountOwner The customer that the account should be linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void processNewAccountReply(final String replyAccountJson, final Customer accountOwner,
                                        final CallbackBuilder callbackBuilder) {
        Account assignedAccount = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyAccountJson), Account.class);
        accountOwner.setAccount(assignedAccount);
        handleNewAccountLinkExceptions(accountOwner, callbackBuilder);
    }

    /**
     * Rejects the request if setting up the account link fails.
     * @param accountOwner Customer object that contains information about the owner of the account, and that contains
     *                     an account object that is to be linked to this customer.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleNewAccountLinkExceptions(final Customer accountOwner, final CallbackBuilder callbackBuilder) {
        try {
            linkAccountToCustomer(accountOwner.getAccount().getAccountNumber(), accountOwner.getCustomerId(), true);
            sendNewAccountLinkCallback(jsonConverter.toJson(accountOwner), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }


    /**
     * Links an accountNumber to a Customer in the Customers database by inserting the customerID and the accountnumber
     * into the accounts table.
     * @throws SQLException Indicates customer account could not be linked.
     * @param customerId Id of the customer to link the account to.
     * @param accountNumber Account number to link to the customer.
     */
    void linkAccountToCustomer(final String accountNumber, final long customerId, final boolean primary) throws SQLException {
        if (!getAccountLinkExistence(accountNumber, customerId)) {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement linkAccountToCustomer = databaseConnection.getConnection()
                                                                        .prepareStatement(addAccountToUser);
            linkAccountToCustomer.setLong(1, customerId);
            linkAccountToCustomer.setString(2, accountNumber);
            linkAccountToCustomer.setBoolean(3, primary);
            linkAccountToCustomer.executeUpdate();
            linkAccountToCustomer.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            System.out.printf("%s Added Accountnumber %s to userid %d\n", PREFIX, accountNumber, customerId);
        }
    }

    /**
     * Checks if an account link for this accountNumber and customerId already exists in the accounts table.
     * @param accountNumber AccountNumber to check for.
     * @param customerId Customer that owns the account.
     * @return If a link exists between accountNumber and customerId.
     * @throws SQLException Indicated the existence could not be verified due to an SQL error.
     */
    boolean getAccountLinkExistence(final String accountNumber, final long customerId) throws SQLException {
        boolean accountLinkExists = false;
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement fetchAccountLinkCount = databaseConnection.getConnection()
                .prepareStatement(getAccountLinkCount);
        fetchAccountLinkCount.setLong(1, customerId);
        fetchAccountLinkCount.setString(2, accountNumber);
        ResultSet accountLinkCount = fetchAccountLinkCount.executeQuery();
        if (accountLinkCount.next() && accountLinkCount.getLong(1) > 0) {
            accountLinkExists = true;
        }
        accountLinkCount.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return accountLinkExists;
    }

    /**
     * Sends a callback to the service that requested the account link/customer creation indicating that the request
     * was successfull.
     * @param newCustomerJson String representing a {@link Customer} containing an {@link Account} with an account
     *                        that was linked to the customer.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendNewAccountLinkCallback(final String newCustomerJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New account successfully linked, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(newCustomerJson);
    }

    /**
     * Takes an account link request, extracts the needed variables and then invokes a check to see if this link
     * already exists.
     * @param callback Used to send a reply back to the service that sent the request.
     * @param accountLinkRequestJson Json string representing an {@link AccountLink} object containing
     *             an account number which is to be attached to the customer with the specified customerId.
     */
    @RequestMapping(value = "/accountLink", method = RequestMethod.PUT)
    public void processAccountLink(final Callback<String> callback,
                                   final @RequestParam("body") String accountLinkRequestJson) {
        System.out.printf("%s Received account link request.\n", PREFIX);
        AccountLink accountLink = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        long customerId = accountLink.getCustomerId();
        String accountNumber = accountLink.getAccountNumber();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountExistsRequest(accountNumber, customerId, callbackBuilder);
    }

    /**
     * Sends a request to the ledger to check if an account exists, if the ledger gives a successfull reply it sends
     * this reply off for processing, otherwise it rejects the request that invoked this method. Used to check if
     * an account exists before linking it to a customer.
     * @param accountNumber AccountNumber to check for.
     * @param customerId Used to link a customer to the account.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doAccountExistsRequest(final String accountNumber, final long customerId,
                                        final CallbackBuilder callbackBuilder) {
        DataRequest accountExistsRequest = JSONParser.createAccountExistsRequest(accountNumber);
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "request",
                                        jsonConverter.toJson(accountExistsRequest),
                                        (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                processAccountExistsReply(dataReplyJson, customerId, callbackBuilder);
            } else {
                System.out.printf("%s Account does not exist, rejecting.\n", PREFIX);
                callbackBuilder.build().reject("Unsuccessfull call, code: " + httpStatusCode);
            }
        });
    }

    /**
     * Checks if the account exists in the ledger, if it does calls the exception handler so the account link can be
     * done, if not it will reject the accountLink request.
     * @param dataReplyJson Json String representing a DataReply object that was received from the ledger.
     * @param customerId Id of the customer the account should be linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void processAccountExistsReply(final String dataReplyJson, final long customerId,
                                           final CallbackBuilder callbackBuilder) {
        DataReply ledgerReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(dataReplyJson), DataReply.class);
        if (ledgerReply.isAccountInLedger()) {
            handleAccountLinkExceptions(ledgerReply.getAccountNumber(), customerId, callbackBuilder);
        } else {
            callbackBuilder.build().reject("Account does not exist.");
        }
    }

    /**
     * Rejects the accountLinkRequest if an SQLException occurs or the customer the account should be linked to does
     * not exist.
     * @param accountNumber AccountNumber of the account to be linked to the customer.
     * @param customerId CustomerId of the customer the account should be linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleAccountLinkExceptions(final String accountNumber, final long customerId,
                                             final CallbackBuilder callbackBuilder) {
        try {
            if (!getCustomerExistence(customerId)) {
                callbackBuilder.build().reject("Account link failed, customer with customerId does not exist.");
            } else {
                linkAccountToCustomer(accountNumber, customerId, false);
                sendAccountLinkCallback(accountNumber, customerId, callbackBuilder);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Throws a CustomerDoesNotExistException if there is no customer in the customers database with Id=customerId.
     * @param customerId Id to look for in the databse.
     * @throws SQLException Exception indicating that something went wrong with our SQL connection.
     * @throws CustomerDoesNotExistException Indicates there is no customer with Id=customerId.
     */
    private void verifyAccountOwnerExistence(final long customerId) throws SQLException, CustomerDoesNotExistException {
        if (!getCustomerExistence(customerId)) {
            throw new CustomerDoesNotExistException("Account link failed, customer with customerId does not exist.");
        }
    }

    /**
     * Sends a callback to the service that sent the accountLinkRequest to this service containing an AccountLink object
     * that represents the executed account link.
     * @param accountNumber AccountNumber of the account that was linked to the customer.
     * @param customerId Id of the customer the account was linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendAccountLinkCallback(final String accountNumber, final long customerId,
                                         final CallbackBuilder callbackBuilder) {
        AccountLink reply = JSONParser.createJsonAccountLink(accountNumber, customerId, true);
        System.out.printf("%s Account link successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(reply));
    }

    /**
     * Takes an account link request, extracts the needed variables and then removes this link from the system.
     * @param callback Used to send a reply back to the service that sent the request.
     * @param accountLinkRequestJson Json string representing an {@link AccountLink} object containing
     *             an account number and customer id, the access of the customer with customer id is removed
     *                               from the account with accountNumber.
     */
    @RequestMapping(value = "/accountLink/remove", method = RequestMethod.PUT)
    public void processAccountLinkRemoval(final Callback<String> callback,
                                          final @RequestParam("request") String accountLinkRequestJson,
                                          final @RequestParam("requesterId") String requesterId) {
        System.out.printf("%s Received account link removal.\n", PREFIX);
        AccountLink accountLink = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        long customerId = accountLink.getCustomerId();
        String accountNumber = accountLink.getAccountNumber();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        verifyAccountLinkPrivelidge(accountNumber, Long.toString(customerId), requesterId, callbackBuilder);
    }

    private void verifyAccountLinkPrivelidge(final String accountNumber, final String customerId,
                                             final String requesterId, final CallbackBuilder callbackBuilder) {
        DataRequest request = new DataRequest();
        request.setType(RequestType.OWNERS);
        LinkedList<String> accountNumberList = new LinkedList<>();
        accountNumberList.add(accountNumber);
        request.setAccountNumbers(accountNumberList);
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "request",
                jsonConverter.toJson(request), (httpStatusCode, httpContentType, dataReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        DataReply dataReply = jsonConverter.fromJson(dataReplyJson, DataReply.class);
                        List<AccountLink> accountLinks = dataReply.getAccounts();
                        AccountLink resultLink = accountLinks.get(0);
                        if (resultLink.getCustomerId().equals(Long.parseLong(requesterId))) {
                            // requester is owner of the account, can revoke access of other customers.
                            removeAccountLink(accountNumber, customerId, callbackBuilder);
                        } else {
                            if (customerId.equals(requesterId)) {
                                removeAccountLink(accountNumber, customerId, callbackBuilder);
                            } else {
                                sendRemoveAccountLinkCallback(false,
                                        "User does not have sufficient permissions.", callbackBuilder);
                            }
                        }
                    } else {
                        sendRemoveAccountLinkCallback(false, "Internal database error",
                                                        callbackBuilder);
                    }
                });
    }

    private void removeAccountLink(final String accountNumber, final String customerId,
                                   final CallbackBuilder callbackBuilder) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement removeAccountLink = databaseConnection.getConnection()
                                                                    .prepareStatement(removeCustomerAccountLink);
            removeAccountLink.setLong(1, Long.parseLong(customerId));
            removeAccountLink.setString(2, accountNumber);
            removeAccountLink.execute();
            removeAccountLink.close();
            databaseConnectionPool.returnConnection(databaseConnection);
        } catch (SQLException e) {
            sendRemoveAccountLinkCallback(false, "No account permissions found.",
                                        callbackBuilder);
        }
    }

    private void sendRemoveAccountLinkCallback(final boolean successfull, final String errorMessage,
                                               final CallbackBuilder callbackBuilder) {
        RemoveAccountLinkReply reply = new RemoveAccountLinkReply();
        reply.setSuccessfull(successfull);
        reply.setErrorMessage(errorMessage);
        callbackBuilder.build().reply(jsonConverter.toJson(reply));
    }

    /**
     * Checks if a customer with id customerId exists in the Customer database.
     * @throws SQLException Indicates customer data could not be fetched.
     * @param customerId Id of the customer to look for.
     * @return Boolean indicating if the customer exists in the Customer database.
     */
    boolean getCustomerExistence(final long customerId) throws SQLException {
        boolean customerExists = false;
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement fetchCustomerCount = databaseConnection.getConnection().prepareStatement(getUserCount);
        fetchCustomerCount.setLong(1, customerId);
        ResultSet customerCount = fetchCustomerCount.executeQuery();
        if (customerCount.next() && customerCount.getLong(1) > 0) {
            customerExists = true;
        }
        customerCount.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return customerExists;
    }

    /**
     * Processes a new account request for an existing customer by loading the customer belonging to the customerId,
     * and then requesting a new account for this customer.
     * @param callback Used to send a reply back to the service that sent the request.
     * @param customerId customerId of the customer the account should be created for.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccount(final Callback<String> callback,
                                  final @RequestParam("customerId") Long customerId) {
        System.out.printf("%s Received account creation request.\n", PREFIX);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewAccountExceptions(customerId, callbackBuilder);
    }

    /**
     * Rejects a new account request if the customer that the account should be created for does not exist or something
     * goes wrong during the database communication.
     * @param customerId CustomerId of the customer the account should be created for.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleNewAccountExceptions(final Long customerId, final CallbackBuilder callbackBuilder) {
        try {
            if (!getCustomerExistence(customerId)) {
                callbackBuilder.build().reject("Account link failed, customer with customerId does not exist.");
            } else {
                Customer accountOwner = getCustomerData(customerId);
                accountOwner.setAccount(new Account(accountOwner.getInitials()
                                                    + accountOwner.getSurname(), 0.0, 0.0));
                doNewAccountRequest(accountOwner, callbackBuilder);
            }
        } catch (SQLException | CustomerDoesNotExistException e) {
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Creates a callbackbuilder so the result of the request can be sent back to the request source and then tries
     * to verify the input of the request, if the input is correct the removal request will be executed and the result
     * will be sent back to the request source, if the input is not correct a rejection will be sent to the
     * request source.
     * @param callback Used to send the result of the request to the request source.
     * @param accountNumber AccountNumber that should be removed from the system.
     * @param customerId CustomerId of the customer that sent the request.
     */
    @RequestMapping(value = "/account/remove", method = RequestMethod.PUT)
    public void processAccountRemoval(final Callback<String> callback,
                                      final @RequestParam("accountNumber") String accountNumber,
                                      final @RequestParam("customerId") String customerId) {
        System.out.printf("%s Received account removal request.\n", PREFIX);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        verifyAccountRemovalInput(accountNumber, Long.parseLong(customerId), callbackBuilder);
    }

    /**
     * Checks if the customer that sent the request exists in the system, and is an owner of the account that should
     * be removed, if one of these conditions is not met the request will be rejected, otherwise the account removal
     * request will be executed.
     * @param accountNumber AccountNumber that should be removed from the system.
     * @param customerId CustomerId of the customer that sent the request.
     * @param callbackBuilder Used to send a reply to the service that sent the request.
     */
    private void verifyAccountRemovalInput(final String accountNumber, final Long customerId,
                                           final CallbackBuilder callbackBuilder) {
        try {
            if (!getCustomerExistence(customerId)) {
                callbackBuilder.build().reject(
                        "Account removal failed, customer with customerId does not exist.");
            } else if (!getAccountLinkExistence(accountNumber, customerId)) {
                callbackBuilder.build().reject(
                        "Account removal failed, this customer is not one of the account owners.");
            } else {
                doAccountRemovalRequest(accountNumber, customerId, callbackBuilder);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    /**
     * Removes the account link that links the account with accountNumber to the customer with customerId from the
     * system.
     * @param accountNumber AccountNumber of the account linked to the customer.
     * @param customerId Id of the customer the account is linked to.
     * @throws SQLException Thrown when the removal query fails.
     */
    private void verifyAccountLinkPrivelidge(final String accountNumber, final Long customerId) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement removeAccountLink = databaseConnection.getConnection()
                                                                .prepareStatement(SQLStatements.removeAccountLinks);
        removeAccountLink.setString(1, accountNumber);
        removeAccountLink.execute();
        removeAccountLink.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Forwards an account removal request to the ledger, and if the account removal is successfull removes all account
     * links for the account from the accounts database and sends a successfull callback to the request source. If the
     * request fails a rejection is sent to the request source.
     * @param accountNumber AccountNumber that should be removed from the system.
     * @param customerId CustomerId of the customer that sent the request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void doAccountRemovalRequest(final String accountNumber, final Long customerId,
                                         final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith2Params("/services/ledger/account/remove", "accountNumber",
        accountNumber, "customerId", Long.toString(customerId), (httpStatusCode, httpContentType, jsonReply) -> {
            if (httpStatusCode == HTTP_OK) {
                try {
                    verifyAccountLinkPrivelidge(accountNumber, customerId);
                    checkIfCustomerOwnsAccounts(customerId, callbackBuilder);
                } catch (SQLException e) {
                    System.out.printf("%s Failed to remove accountLink, sending rejection.\n", PREFIX);
                    callbackBuilder.build().reject(e.getMessage());
                }
            } else {
                callbackBuilder.build().reject("Couldn't reach transactionDispatch.");
            }
        });
    }

    /**
     * Checks if the customer still owns accounts in the system, and if the customer does not the customer is
     * removed from the system.
     * @param customerId Id of the customer to check accounts for.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void checkIfCustomerOwnsAccounts(final Long customerId, CallbackBuilder callbackBuilder)
                                             throws SQLException {
        List<String> customerAccounts = getCustomerAccounts(customerId);
        if (customerAccounts.size() > 0) {
            DataRequest request = new DataRequest();
            request.setType(RequestType.OWNERS);
            request.setAccountNumbers(customerAccounts);
            ledgerClient.getAsyncWith1Param("/services/ledger/data", "request",
                    jsonConverter.toJson(request), (httpStatusCode, httpContentType, dataReplyJson) -> {
                        if (httpStatusCode == HTTP_OK) {
                            DataReply dataReply = jsonConverter.fromJson(dataReplyJson, DataReply.class);
                            List<AccountLink> accountLinks = dataReply.getAccounts();
                            int accountsOwned = 0;
                            for (AccountLink link : accountLinks) {
                                if (link.getCustomerId().equals(customerId)) {
                                    accountsOwned += 1;
                                }
                            }
                            boolean removedCustomer = false;
                            if (accountsOwned < 1) {
                                removeCustomer(customerId);
                                removedCustomer = true;
                            }
                            sendAccountRemovalCallback(removedCustomer, true, "", callbackBuilder);
                        } else {
                            sendAccountRemovalCallback(false, false,
                                                        "Internal database error", callbackBuilder);
                        }
                    });
        } else {
            removeCustomer(customerId);
            sendAccountRemovalCallback(true, true, "", callbackBuilder);
        }
    }

    /**
     * Removes a customer from the users database.
     * @param customerId Id of the customer to remove.
     */
    private void removeCustomer(final Long customerId) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement removeCustomer = databaseConnection.getConnection()
                    .prepareStatement(SQLStatements.removeCustomer);
            removeCustomer.setLong(1, customerId);
            removeCustomer.execute();
            removeCustomer.close();
            PreparedStatement removeLinks = databaseConnection.getConnection().prepareCall(removeCustomerLinks);
            removeLinks.setLong(1, customerId);
            removeLinks.execute();
            removeLinks.close();
            databaseConnectionPool.returnConnection(databaseConnection);
        } catch (SQLException e) {
            System.out.printf("%s Failed to remove customer from system", PREFIX);
        }
    }

    private void sendAccountRemovalCallback(final boolean removedCustomer, final boolean successfull,
                                            final String errorMessage, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account removal successfull, sending callback.\n", PREFIX);
        CloseAccountReply reply = JSONParser.createCloseAccountReply(removedCustomer, successfull, errorMessage);
        callbackBuilder.build().reply(jsonConverter.toJson(reply));
    }

    /**
     * Safely shuts down the UsersService.
     */
    void shutdown() {
        if (ledgerClient != null) ledgerClient.stop();
        if (transactionDispatchClient != null) transactionDispatchClient.stop();
        if (databaseConnectionPool != null) databaseConnectionPool.close();
    }
}
