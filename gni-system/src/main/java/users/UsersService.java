package users;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
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
import java.util.List;

import static database.SQLStatements.*;
import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * The Users microservice, handles customer information and is used as a gateway for the UI service.
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
     * Processes incoming data requests from the UI service and sends a reply back through a callback, if necessary
     * sends the request to the LedgerService service and waits for a callback from the LedgerService.
     * @param callback Used to send result back to the UI service.
     * @param requestJson Json String representing a DataRequest that is made by the UI service {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, final @RequestParam("body") String requestJson) {
        DataRequest dataRequest = jsonConverter.fromJson(requestJson, DataRequest.class);
        RequestType dataRequestType = dataRequest.getType();
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        if (dataRequestType == RequestType.CUSTOMERDATA || dataRequestType == RequestType.ACCOUNTS) {
            System.out.println("Users: Called by UI, fetching customer data.");
            handleInternalDataRequest(dataRequest, callbackBuilder);
        } else {
            System.out.println("Users: Called by UI, calling Ledger");
            doLedgerDataRequest(dataRequest, callbackBuilder);
        }
    }

    private void handleInternalDataRequest(final DataRequest dataRequest, final CallbackBuilder callbackBuilder) {
        if (dataRequest.getType() == RequestType.ACCOUNTS) {
            handleAccountsRequestExceptions(dataRequest.getCustomerId(), callbackBuilder);
        } else {             //The request is a Customer Data request.
            handleCustomerDataRequestExceptions(dataRequest.getCustomerId(), callbackBuilder);
        }
    }

    private void handleAccountsRequestExceptions(final Long customerId, final CallbackBuilder callbackBuilder) {
        try {
            sendAccountsRequestCallback(getCustomerAccounts(customerId), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    private void sendAccountsRequestCallback(final DataReply customerAccounts, final CallbackBuilder callbackBuilder)
            throws SQLException {
        callbackBuilder.build().reply(jsonConverter.toJson(customerAccounts));
        System.out.println("Users: Sent accounts request callback to UI.");
    }

    private void handleCustomerDataRequestExceptions(final Long customerId, final CallbackBuilder callbackBuilder) {
        try {
            sendCustomerDataRequestCallback(getCustomerData(customerId), callbackBuilder);
        } catch (SQLException | CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    private void sendCustomerDataRequestCallback(final Customer customerData, final CallbackBuilder callbackBuilder) {
        callbackBuilder.build().reply(jsonConverter.toJson(customerData));
        System.out.println("Users: Sent customer data request callback to UI.");
    }

    /**
     * Fetches account numbers from the accounts table for the customer with the respective id, returns this in a
     * list object.
     * @throws SQLException Indicates customer accounts could not be fetched.
     * @param customerId Customer id of the customer we want to fetch accounts for.
     * @return List containing account number that belong to the customer, null if no account numbers belong to the
     *         customer.
     */
    private DataReply getCustomerAccounts(final Long customerId) throws SQLException {
        List<String> linkedAccounts = new ArrayList<>();
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getAccountsFromDb = databaseConnection.getConnection().prepareStatement(getAccountNumbers);
        getAccountsFromDb.setLong(1, customerId);
        ResultSet retrievedAccounts = getAccountsFromDb.executeQuery();
        DataReply customerAccounts = new DataReply();
        customerAccounts.setType(RequestType.ACCOUNTS);
        while (retrievedAccounts.next()) {
            linkedAccounts.add(retrievedAccounts.getString("account_number"));
        }
        getAccountsFromDb.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        customerAccounts.setAccounts(linkedAccounts);
        return customerAccounts;
    }

    /**
     * Fetches customer data from the customers table for the customer with id customerId and returns
     * this data in a Customer object.
     * @throws SQLException Indicates customer data could not be fetched.
     * @throws CustomerDoesNotExistException Indicated there is no customer with that customer id.
     * @param customerId Id of the customer to fetch data for.
     * @return Customer object containing the data for Customer with id=customerId
     */
    private Customer getCustomerData(final Long customerId) throws SQLException, CustomerDoesNotExistException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCustomerDataFromDb = databaseConnection.getConnection()
                                                  .prepareStatement(getUserInformation);
        getCustomerDataFromDb.setLong(1, customerId);
        ResultSet retrievedCustomerData = getCustomerDataFromDb.executeQuery();
        Customer customerData = new Customer();
        if (retrievedCustomerData.next()) {
            customerData.setId(customerId);
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
     * Sends a data request to the LedgerService and handles the response from the ledger.
     * Uses the callbackBuilder to send the reply from the ledger back to the UI service.
     * @param dataRequestJson DataRequest that was sent to the ledger {@link DataRequest}.
     * @param callbackBuilder Used to send the reply of the ledger back to the UI service.
     */
    private void doLedgerDataRequest(final DataRequest dataRequest, final CallbackBuilder callbackBuilder) {
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "body",
                                        jsonConverter.toJson(dataRequest),
                                        (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                sendLedgerDataRequestCallback(replyJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Received an error from ledger.");
            }
        });
    }

    private void sendLedgerDataRequestCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        callbackBuilder.build().reply(JSONParser.sanitizeJson(replyJson));
        System.out.println("Users: Sent ledger data request callback to UI.");
    }


    /**
     * Processes transaction requests coming from the UI service by forwarding them to the TransactionDispatch service.
     * @param callback Used to send the result back to the UI service.
     * @param requestJson Json String containing a Transaction object for a transaction request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransaction(final Callback<String> callback, final @RequestParam("body") String requestJson) {
        Transaction transactionRequest = jsonConverter.fromJson(requestJson, Transaction.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.println("Users: Sent transaction to TransactionDispatch");
        doTransactionRequest(transactionRequest, callbackBuilder);
    }

    /**
     * Sends transaction request to the TransactionDispatch service and sends the reply back to the UI service.
     * @param transactionRequest Transaction request made by the UI service {@link Transaction}.
     * @param callbackBuilder Used to send the result back to the UI service.
     */
    private void doTransactionRequest(final Transaction transactionRequest, final CallbackBuilder callbackBuilder) {
        transactionDispatchClient.putFormAsyncWith1Param("/services/transactionDispatch/transaction",
                                                        "body", jsonConverter.toJson(transactionRequest),
                                                        (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                processTransactionReply(replyJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Couldn't reach transactionDispatch.");
            }
        });
    }

    private void processTransactionReply(final String replyJson, final CallbackBuilder callbackBuilder) {
        Transaction transactionReply = jsonConverter.fromJson(replyJson
                                                              .substring(1, replyJson.length() - 1)
                                                              .replaceAll("\\\\", ""), Transaction.class);
        if (transactionReply.isProcessed() && transactionReply.isSuccessful()) {
            sendTransactionRequestCallback(transactionReply, callbackBuilder);
        } else {
            callbackBuilder.build().reject("Transaction failed, processed: "
                                            + transactionReply.isProcessed() + " successfull: "
                                            + transactionReply.isSuccessful());
        }
    }

    private void sendTransactionRequestCallback(final Transaction transactionReply,
                                                final CallbackBuilder callbackBuilder) {
        callbackBuilder.build().reply(jsonConverter.toJson(transactionReply));
        System.out.println("Users: Transaction was successfull, sent callback to UI.");
    }

    /**
     * Processes customer creation requests coming from the UI service, sends the request to the LedgerService service
     * to obtain an accountNumber for the customer and then processes the customer in the User database.
     * @param callback Used to send the result of the request back to the UI service.
     * @param requestJson Json string containing the Customer object the request is for {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    //todo rewrite so we can use initials + surname for accountholdername.
    public void processNewCustomer(final Callback<String> callback, final @RequestParam("body") String requestJson) {
        Customer customerToEnroll = jsonConverter.fromJson(requestJson, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewCustomerRequestExceptions(customerToEnroll, callbackBuilder);
    }

    private void handleNewCustomerRequestExceptions(final Customer customerToEnroll,
                                                    final CallbackBuilder callbackBuilder) {
        try {
            customerToEnroll.setId(getNewCustomerID());
            enrollCustomer(customerToEnroll);
            doAccountNumberRequest(customerToEnroll, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    private Long getNewCustomerID() throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        Long newCustomerID = databaseConnection.getNextID(getNextUserID);
        databaseConnectionPool.returnConnection(databaseConnection);
        return newCustomerID;
    }

    /**
     * Enrolls the customer in the Users database.
     * @throws SQLException Indicates that something went wrong when enrolling the user into the system(failed).
     * @param customer Customer to enroll in the database.
     */
    //todo implement check to see if the customer already exists in the database.
    private void enrollCustomer(final Customer customer) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement createNewCustomer = databaseConnection.getConnection().prepareStatement(createNewUser);
        createNewCustomer.setLong(1, customer.getId());                // id
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
        System.out.println("Users: New customer successfully enrolled.");
    }

    /**
     * Sends request for obtaining an accountNumber to the ledger, then processes the customer request internally in
     * the User database and sends a reply back to the UI service.
     * @param customerRequest Customer object that was used to make a new customer request.
     * @param callbackBuilder Used to send the result of the customer request back to the UI service.
     */
    //todo rename to a more appropriate function name, split up into more uniform functions
    private void doAccountNumberRequest(final Customer accountOwner, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body",
                                            jsonConverter.toJson(accountOwner.getAccount()),
                                            (httpStatusCode, httpContentType, replyAccountJson) -> {
            if (httpStatusCode == HTTP_OK) {
                processAccountNumberReply(replyAccountJson, accountOwner, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Received an error from ledger.");
            }
        });
    }

    private void processAccountNumberReply(final String replyAccountJson, final Customer accountOwner,
                                           final CallbackBuilder callbackBuilder) {
        Account assignedAccount = jsonConverter.fromJson(replyAccountJson
                .substring(1, replyAccountJson.length() - 1)
                .replaceAll("\\\\", ""), Account.class);
        accountOwner.setAccount(assignedAccount);
        handleNewAccountLinkExceptions(accountOwner, callbackBuilder);
    }

    private void handleNewAccountLinkExceptions(final Customer accountOwner, final CallbackBuilder callbackBuilder) {
        try {
            linkAccountToCustomer(accountOwner.getAccount().getAccountNumber(), accountOwner.getId());
            sendNewAccountLinkCallback(accountOwner, callbackBuilder);
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
    private void linkAccountToCustomer(final String accountNumber, final Long customerId) throws SQLException {
        if (!getAccountLinkExistence(accountNumber, customerId)) {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement linkAccountToCustomer = databaseConnection.getConnection()
                                                                        .prepareStatement(addAccountToUser);
            linkAccountToCustomer.setLong(1, customerId);
            linkAccountToCustomer.setString(2, accountNumber);
            linkAccountToCustomer.executeUpdate();
            linkAccountToCustomer.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            System.out.printf("Users: Added Accountnumber %s to userid %d\n", accountNumber, customerId);
        }
    }

    private boolean getAccountLinkExistence(final String accountNumber, final Long customerId) throws SQLException {
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

    private void sendNewAccountLinkCallback(final Customer newCustomer, final CallbackBuilder callbackBuilder) {
        callbackBuilder.build().reply(newCustomer);
        System.out.println("Users: New account successfully linked, sent callback to UI.");
    }

    /**
     * Takes an account link request and links the account to the specified customer id.
     * @param callback Used to send the result back to the UI Service.
     * @param body Json string representing an AccountLink{@link AccountLink} object containing
     *             an account number which is to be attached to a customer with the specified customerId.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void processAccountLink(final Callback<String> callback, final @RequestParam("body") String body) {
        AccountLink accountLink = jsonConverter.fromJson(body, AccountLink.class);
        Long customerId = accountLink.getCustomerId();
        String accountNumber = accountLink.getAccountNumber();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountExistsRequest(accountNumber, customerId, callbackBuilder);
    }

    /**
     * Checks if an account exists in the ledger, if it does this is an existing account that will be added to the
     * customer specified in request. Otherwise the request will be rejected.
     * @param accountExistsRequest DataRequest object containing an account link request.
     * @param customerId Id of the customer to link the account to.
     * @param callbackBuilder Used to send a callback containing the result to UI.
     */
    private void doAccountExistsRequest(final String accountNumber, final Long customerId,
                                        final CallbackBuilder callbackBuilder) {
        DataRequest accountExistsRequest = JSONParser.createAccountExistsRequest(accountNumber);
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "body",
                                        jsonConverter.toJson(accountExistsRequest),
                                        (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                processAccountExistsReply(replyJson, customerId, accountExistsRequest, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Unsuccessfull call, code: " + httpStatusCode);
            }
        });
    }

    private void processAccountExistsReply(final String replyJson, final Long customerId,
                                           final DataRequest accountExistsRequest,
                                           final CallbackBuilder callbackBuilder) {
        DataReply ledgerReply = jsonConverter.fromJson(replyJson.substring(1, replyJson.length() - 1)
                                                                .replaceAll("\\\\", ""), DataReply.class);
        if (ledgerReply.isAccountInLedger()) {
            handleAccountLinkExceptions(customerId, ledgerReply.getAccountNumber(), callbackBuilder);
        } else {
            callbackBuilder.build().reject("Account does not exist.");
        }
    }

    private void handleAccountLinkExceptions(final Long customerId, final String accountNumber,
                                             final CallbackBuilder callbackBuilder) {
        try {
            verifyAccountOwnerExistence(customerId);
            linkAccountToCustomer(accountNumber, customerId);
            sendAccountLinkCallback(accountNumber, customerId, callbackBuilder);
        } catch (SQLException | CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reject(e);
        }
    }

    private void verifyAccountOwnerExistence(final Long customerId) throws SQLException, CustomerDoesNotExistException {
        if (!getCustomerExistence(customerId)) {
            throw new CustomerDoesNotExistException("Account link failed, customer with customerId does not exist.");
        }
    }

    private void sendAccountLinkCallback(final String accountNumber, final Long customerId,
                                         final CallbackBuilder callbackBuilder) {
        AccountLink reply = JSONParser.createJsonAccountLink(customerId, accountNumber, true);
        System.out.println("Users: Account link successfull.");
        callbackBuilder.build().reply(jsonConverter.toJson(reply));
    }

    /**
     * Checks if a customer with id customerId exists in the Customer database.
     * @throws SQLException Indicates customer data could not be fetched.
     * @param customerId Id of the customer to look for.
     * @return Boolean indicating if the customer exists in the Customer database.
     */
    private boolean getCustomerExistence(final Long customerId) throws SQLException {
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

    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccount(final Callback<String> callback,
                                             final @RequestParam("body") String requestJson) {
        System.out.println("Users: Received account creation request.");
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        Customer accountOwner = jsonConverter.fromJson(requestJson, Customer.class);
        handleNewAccountExceptions(accountOwner, callbackBuilder);
    }

    private void handleNewAccountExceptions(final Customer accountOwner, final CallbackBuilder callbackBuilder) {
        try {
            verifyAccountOwnerExistence(accountOwner.getId());
            doAccountNumberRequest(accountOwner, callbackBuilder);
        } catch (SQLException | CustomerDoesNotExistException e) {
            callbackBuilder.build().reject(e);
        }
    }
}
