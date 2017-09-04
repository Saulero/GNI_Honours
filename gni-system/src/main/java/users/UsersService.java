package users;

import api.methods.TransferBankAccount;
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
    /** Connection to the Transaction Receive Service.*/
    private HttpClient transactionReceiveClient;
    /** Connection to the Pin Service.*/
    private HttpClient pinClient;
    /** Connection to the SystemInformation service. */
    private HttpClient systemInformationClient;
    /** Connection pool with database connections for the User Service. */
    private ConnectionPool databaseConnectionPool;
    /** Gson object used to convert objects to/from json. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Users Service. */
    private static final String PREFIX = "[Users]               :";

    /**
     * Constructor.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    UsersService(final int servicePort, final String serviceHost,
               final int sysInfoPort, final String sysInfoHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
        this.databaseConnectionPool = new ConnectionPool();
        this.jsonConverter = new Gson();
        sendServiceInformation(servicePort, serviceHost);
    }

    /**
     * Method that sends the service information of this service to the SystemInformationService.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     */
    private void sendServiceInformation(final int servicePort, final String serviceHost) {
        ServiceInformation serviceInfo = new ServiceInformation(servicePort, serviceHost, ServiceType.USERS_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", jsonConverter.toJson(serviceInfo), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        System.err.println("Problem with connection to the SystemInformationService.");
                        System.err.println("Shutting down the Users service.");
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
        ServiceInformation ledger = sysInfo.getLedgerServiceInformation();
        ServiceInformation transactionDispatch = sysInfo.getTransactionDispatchServiceInformation();
        ServiceInformation transactionReceive = sysInfo.getTransactionReceiveServiceInformation();
        ServiceInformation pin = sysInfo.getPinServiceInformation();

        this.ledgerClient = httpClientBuilder().setHost(ledger.getServiceHost())
                .setPort(ledger.getServicePort()).buildAndStart();
        this.transactionDispatchClient = httpClientBuilder().setHost(transactionDispatch.getServiceHost())
                .setPort(transactionDispatch.getServicePort()).buildAndStart();
        this.transactionReceiveClient = httpClientBuilder().setHost(transactionReceive.getServiceHost())
                .setPort(transactionReceive.getServicePort()).buildAndStart();
        this.pinClient = httpClientBuilder().setHost(pin.getServiceHost()).setPort(pin.getServicePort()).buildAndStart();

        System.out.printf("%s Initialization of Users service connections complete.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
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
     * @param data Json String representing a {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   final @RequestParam("data") String data) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        if (messageWrapper.getMethodType() == MethodType.GET_TRANSACTION_OVERVIEW
                || ((DataRequest) messageWrapper.getData()).getType() == RequestType.ACCOUNTEXISTS) {
            doLedgerDataRequest(messageWrapper, false, callbackBuilder);
        } else if (messageWrapper.getMethodType() == MethodType.GET_BALANCE) {
            doLedgerDataRequest(messageWrapper, true, callbackBuilder);
        } else {
            handleInternalDataRequest(messageWrapper, callbackBuilder);
        }
    }

    /**
     * Checks which type if internal data request needs to be processed and then calls the exception handler for the
     * respective type.
     * @param dataRequest Data request that needs to be handled.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleInternalDataRequest(final MessageWrapper dataRequest, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Received customer data request, fetching data.\n", PREFIX);
        switch (dataRequest.getMethodType()) {
            case GET_BANK_ACCOUNT_ACCESS:
                handleAccountAccessListRequestExceptions(dataRequest, callbackBuilder);
                break;
            case GET_USER_ACCESS:
                handleCustomerAccessListRequestExceptions(
                        ((DataRequest) dataRequest.getData()).getCustomerId(), callbackBuilder);
                break;
            default:
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Internal system error occurred.", "Incorrect requestType specified.")));
                break;
        }
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
        customerAccounts.setType(RequestType.CUSTOMERACCESSLIST);
        List<String> linkedAccounts = new LinkedList<>();
        while (retrievedAccounts.next()) {
            linkedAccounts.add(retrievedAccounts.getString("account_number"));
        }
        getAccountsFromDb.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return  linkedAccounts;
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
     * Sends a reject to the service that sent the data request if the SQL query in getAccountAccessList fails or the
     * account does not exist.
     * @param messageWrapper MessageWrapper containing the request
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleAccountAccessListRequestExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        DataRequest dataRequest = (DataRequest) messageWrapper.getData();
        try {
            if (!messageWrapper.isAdmin()
                    && !isCustomerPrimaryOwner(dataRequest.getAccountNumber(), dataRequest.getCustomerId())) {
                throw new users.UserNotAuthorizedException(
                        "The customer is not the primary owner of the provided bank account.");
            }
            DataReply reply = new DataReply(
                    RequestType.ACCOUNTACCESSLIST, getAccountAccessList(dataRequest.getAccountNumber()));
            sendAccountAccessListRequestCallback(reply, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to Users database.")));
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.")));
        } catch (AccountDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 418,
                    "One of the parameters has an invalid value.",
                    "The provided account does not seem to exist.")));
        }
    }

    /**
     * Sends a list of customers that have access to a given account to the service that requested it.
     * Logs this is system.out.
     * @param reply DataReply containing a list of customers who have access to the requested account.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendAccountAccessListRequestCallback(final DataReply reply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending account access list request callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", reply)));
    }

    private long getPrimaryOwner(final String accountNumber) throws SQLException, AccountDoesNotExistException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection().prepareStatement(getPrimaryAccountOwner);
        ps.setString(1, accountNumber);
        ResultSet rs = ps.executeQuery();
        long res;

        if (rs.next()) {
            res = rs.getLong("user_id");
        } else {
            throw new AccountDoesNotExistException("No AccountLinks were found.");
        }

        rs.close();
        ps.close();
        databaseConnectionPool.returnConnection(con);
        return res;
    }

    private boolean isCustomerPrimaryOwner(final String accountNumber, final long customerID)
            throws SQLException, AccountDoesNotExistException {
        return getPrimaryOwner(accountNumber) == customerID;
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
     * Sends a reject to the calling service if the SQL query in getCustomerAccounts fails.
     * @param customerId Id of the customer whose accounts we want to fetch.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void handleCustomerAccessListRequestExceptions(final long customerId, final CallbackBuilder callbackBuilder) {
        try {
            sendCustomerAccessListRequestCallback(processCustomerAccessListRequest(customerId), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
        } catch (CustomerDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "The provided customer does not seem to exist.")));
        }
    }

    /**
     * Send a DataReply object containing accounts belonging to a certain customer to the service that requested them.
     * @param customerAccounts DataReply object containing a list of accounts belonging to a certain customer.
     * @param callbackBuilder Used to send a reply back to the calling service.
     */
    private void sendCustomerAccessListRequestCallback(final DataReply customerAccounts, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Sending accounts request callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", customerAccounts)));
    }

    //todo write unit test for this method. in fact, update all Junit
    private DataReply processCustomerAccessListRequest(final long customerID) throws SQLException, CustomerDoesNotExistException {
        LinkedList<AccountLink> res = new LinkedList<>();
        for (String s : this.getCustomerAccounts(customerID)) {
            res.add(new AccountLink(customerID, s));
        }
        return new DataReply(RequestType.CUSTOMERACCESSLIST, res);
    }

    /**
     * Forwards a data request to the ledger, sends a callback if the request succeeds, else rejects the callback.
     * @param dataRequest Data request that needs to be sent to the ledger.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doLedgerDataRequest(final MessageWrapper dataRequest, final boolean getCreditCardData,
                                     final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Called for a data request, calling Ledger.\n", PREFIX);
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "data",
                                        jsonConverter.toJson(dataRequest),
                                        (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    if (getCreditCardData) {
                        doCreditCardBalanceRequest(dataRequest, dataReplyJson, callbackBuilder);
                    } else {
                        sendLedgerDataRequestCallback(dataReplyJson, callbackBuilder);
                    }
                } else {
                    callbackBuilder.build().reply(dataReplyJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
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
        callbackBuilder.build().reply(dataReplyJson);
    }

    private void doCreditCardBalanceRequest(final MessageWrapper datarequest, final String ledgerReplyJson,
                                            final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding dataRequest to pin.\n", PREFIX);
        DataRequest request = (DataRequest) datarequest.getData();
        pinClient.getAsyncWith1Param("/services/pin/creditCard/Balance", "accountNumber",
                request.getAccountNumber(), (httpStatusCode, httpContentType, dataReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper pinReply = jsonConverter.fromJson(JSONParser
                                                        .removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                        MessageWrapper ledgerReply = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(ledgerReplyJson),
                                                                            MessageWrapper.class);
                        DataReply ledgerData = (DataReply) ledgerReply.getData();
                        if (!pinReply.isError()) {
                            Double creditCardBalance = (Double) pinReply.getData();
                            Account completeBalanceOverview = ledgerData.getAccountData();
                            completeBalanceOverview.setCreditCardBalance(creditCardBalance);
                            ledgerData.setAccountData(completeBalanceOverview);
                            String responseJson = jsonConverter.toJson(JSONParser.createMessageWrapper(false,
                                    200, "normal reply", ledgerData));
                            sendLedgerDataRequestCallback(responseJson, callbackBuilder);
                        } else if (pinReply.getCode() == 418) {
                            // no card for this account
                            System.out.printf("%s No credit card linked to this account.\n", PREFIX);
                            sendLedgerDataRequestCallback(ledgerReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(dataReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }


    /**
     * Processes a transaction request by forwarding it to the TransactionDispatch service.
     * @param callback Used to send the result back to the calling service.
     * @param requestWrapper Json representation of a messageWrapper containing a transaction request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransaction(final Callback<String> callback,
                                   final @RequestParam("request") String requestWrapper,
                                   final @RequestParam("customerId") String customerId) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(requestWrapper),
                                                                MessageWrapper.class);
        Transaction transactionRequest = (Transaction) messageWrapper.getData();
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.printf("%s Sending transaction to TransactionDispatch.\n", PREFIX);
        doTransactionRequest(transactionRequest, customerId, messageWrapper, callbackBuilder);
    }

    /**
     * Sends transaction request to the TransactionDispatch service and processes the reply.
     * @param transactionRequest  {@link Transaction} request forwarded by the Authentication service.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doTransactionRequest(final Transaction transactionRequest, final String customerId,
                                      final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            if (checkIfFrozen(transactionRequest.getSourceAccountNumber())) {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.
                        createMessageWrapper(true, 419,
                                "The user is not authorized to perform this action.", "User has no authorization to do this as long as the account is frozen.")));
                return;
            }
            if (checkIfFrozen(transactionRequest.getDestinationAccountNumber())) {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.
                        createMessageWrapper(true, 419,
                                "The user is not authorized to perform this action.", "The provided destination account has been frozen and can't receive transactions.")));
                return;
            }
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
            return;
        }

        transactionDispatchClient.putFormAsyncWith3Params("/services/transactionDispatch/transaction",
                                                        "request", jsonConverter.toJson(messageWrapper),
                                                        "customerId", customerId,
                                                        "override", false,
                                                        (httpStatusCode, httpContentType, transactionReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper responseWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                if (!responseWrapper.isError()) {
                    processTransactionDispatchReply((Transaction) responseWrapper.getData(), transactionReplyJson, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(transactionReplyJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    private boolean checkIfFrozen(final String accountNumber) throws SQLException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection().prepareStatement(checkIfFrozen);
        ps.setString(1, accountNumber);
        ResultSet rs = ps.executeQuery();
        boolean res = true;
        if (rs.next()) {
            int accountCount = rs.getInt(1);
            if (accountCount == 0) {
                res = false;
            }
        }
        ps.close();
        databaseConnectionPool.returnConnection(con);
        return res;
    }

    @RequestMapping(value = "/setFreezeUserAccount", method = RequestMethod.PUT)
    public void processSetFreezeUserAccountRequest(
            final Callback<String> callback, @RequestParam("request") final String dataJson) {
        System.out.printf("%s Received setFreezeUserAccount request.\n", PREFIX);
        FreezeAccount freezeAccount = jsonConverter.fromJson(dataJson, FreezeAccount.class);

        try {
            setFreezeUserAccount(freezeAccount);
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    false, 200, "Normal Reply")));
        } catch (SQLException e) {
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the Pin database.")));
        }
    }

    private void setFreezeUserAccount(final FreezeAccount freezeAccount) throws SQLException {
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection().prepareStatement(setFreezeStatusUsers);
        ps.setLong(2, freezeAccount.getCustomerId());
        ps.setBoolean(1, freezeAccount.getFreeze());
        ps.executeUpdate();
        ps.close();
        databaseConnectionPool.returnConnection(con);
    }

    /**
     * Checks if the transaction was processed and successful, and then invokes the corresponding callback.
     * @param transaction A Transaction reply that was received from the TransactionDispatchService.
     * @param transactionReplyJson Original Json String
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void processTransactionDispatchReply(final Transaction transaction, final String transactionReplyJson, final CallbackBuilder callbackBuilder) {
        if (transaction.isProcessed() && transaction.isSuccessful()) {
            transactionReceiveClient.putFormAsyncWith1Param("/services/transactionReceive/transaction",
                    "request", jsonConverter.toJson(transaction),
                    (statusCode, httpContentType, replyBody) -> processTransactionReceiveReply(statusCode, replyBody, callbackBuilder));
            sendTransactionRequestCallback(transactionReplyJson, callbackBuilder);
        } else {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.")));
        }
    }

    private void processTransactionReceiveReply(final int statusCode,
                                                final String replyBody,
                                                final CallbackBuilder callbackBuilder) {
        if (statusCode == HTTP_OK) {
            MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyBody),
                    MessageWrapper.class);
            if (!messageWrapper.isError()) {
                Transaction reply = (Transaction) messageWrapper.getData();
                if (reply.isSuccessful()) {
                    System.out.printf("%s Transaction was successful, sending callback.\n", PREFIX);
                    callbackBuilder.build().reply(replyBody);
                } else {
                    System.out.printf("%s Transaction was unsuccessful, sending rejection.\n", PREFIX);
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                            "Unknown error occurred.")));
                }
            } else {
                callbackBuilder.build().reply(replyBody);
            }
        } else {
            System.out.printf("%s Transaction request failed, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true,
                    500, "An unknown error occurred.",
                    "There was a problem with one of the HTTP requests")));
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
        System.out.printf("%s Transaction was successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(transactionReplyJson);
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
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to authentication database.")));
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
        return newCustomerId + 10;
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
     *                     account object with a specified accountHolderName, balance and overdraft limit.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doNewAccountRequest(final Customer accountOwner, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/account", "body",
                                            jsonConverter.toJson(accountOwner.getAccount()),
                                            (httpStatusCode, httpContentType, replyAccountJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(replyAccountJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    processNewAccountReply((Account) messageWrapper.getData(), accountOwner, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(replyAccountJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Processes a reply from the ledger containing a new account which is to be linked to a customer.
     * @param replyAccount Account that should be linked to a {@link Customer}.
     * @param accountOwner The customer that the account should be linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void processNewAccountReply(final Account replyAccount, final Customer accountOwner,
                                        final CallbackBuilder callbackBuilder) {
        accountOwner.setAccount(replyAccount);
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
            sendNewAccountLinkCallback(accountOwner, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
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
            linkAccountToCustomer.setBoolean(4, false);
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
     * was successful.
     * @param newCustomer A Customer containing an Account with an account that was linked to the customer.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendNewAccountLinkCallback(final Customer newCustomer, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New account successfully linked, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", newCustomer)));
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
                                   final @RequestParam("body") String accountLinkRequestJson,
                                   final @RequestParam("requesterId") String requesterId) {
        System.out.printf("%s Received account link request.\n", PREFIX);
        AccountLink accountLink = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        long customerId = accountLink.getCustomerId();
        String accountNumber = accountLink.getAccountNumber();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountExistsRequest(accountNumber, customerId, requesterId, callbackBuilder);
    }

    /**
     * Sends a request to the ledger to check if an account exists, if the ledger gives a successful reply it sends
     * this reply off for processing, otherwise it rejects the request that invoked this method. Used to check if
     * an account exists before linking it to a customer.
     * @param accountNumber AccountNumber to check for.
     * @param customerId Used to link a customer to the account.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void doAccountExistsRequest(final String accountNumber, final long customerId, final String requesterId,
                                        final CallbackBuilder callbackBuilder) {
        DataRequest accountExistsRequest = new DataRequest(accountNumber, RequestType.ACCOUNTEXISTS, customerId);
        MessageWrapper request = JSONParser.createMessageWrapper(false, 0, "Request", accountExistsRequest);
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "data", jsonConverter.toJson(request),
                (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(
                        JSONParser.removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    processAccountExistsReply(
                            (DataReply) messageWrapper.getData(), customerId, requesterId, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(dataReplyJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                        "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Checks if the account exists in the ledger, if it does calls the exception handler so the account link can be
     * done, if not it will reject the accountLink request.
     * @param dataReply A DataReply object that was received from the ledger.
     * @param customerId Id of the customer the account should be linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void processAccountExistsReply(final DataReply dataReply, final long customerId, final String requesterId,
                                           final CallbackBuilder callbackBuilder) {
        if (dataReply.isAccountInLedger()) {
            handleAccountLinkExceptions(dataReply.getAccountNumber(), customerId, requesterId, callbackBuilder);
        } else {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "Account does not appear to exist.")));
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
                                             final String requesterId, final CallbackBuilder callbackBuilder) {
        try {
            if (!getCustomerExistence(customerId)) {
                throw new AccountDoesNotExistException("Account link failed, customer with customerId does not exist.");
            } else {
                if (isCustomerPrimaryOwner(accountNumber, Long.parseLong(requesterId))) {
                    linkAccountToCustomer(accountNumber, customerId, false);
                    sendAccountLinkCallback(accountNumber, customerId, callbackBuilder);
                } else {
                    throw new UserNotAuthorizedException("Account link failed, customer not authorized to provide access.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to the Users database.")));
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419, "The user is not authorized to perform this action.", e.getMessage())));
        } catch (AccountDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
        }
    }

    /**
     * Sends a callback to the service that sent the accountLinkRequest to this service containing an AccountLink object
     * that represents the executed account link.
     * @param accountNumber AccountNumber of the account that was linked to the customer.
     * @param customerId Id of the customer the account was linked to.
     * @param callbackBuilder Used to send a reply back to the service that sent the request.
     */
    private void sendAccountLinkCallback(final String accountNumber, final long customerId, final CallbackBuilder callbackBuilder) {
        AccountLink reply = new AccountLink(customerId, accountNumber, true);
        System.out.printf("%s Account link successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", reply)));
    }

    /**
     * Takes an account link removal request, extracts the needed variables and then removes this link from the system.
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
        removeAccountLinks(accountNumber, Long.toString(customerId), requesterId, callbackBuilder);
    }

    private void removeAccountLinks(final String accountNumber, final String customerId,
                                    final String requesterId, final CallbackBuilder callbackBuilder) {
        try {
            long requester = Long.parseLong(requesterId);
            if (getPrimaryOwner(accountNumber) == requester) {
                // requester is owner of the account, can revoke access of other customers.
                if (!customerId.equals(requesterId)) {
                    removeAccountLink(accountNumber, customerId);
                    sendRemoveAccountLinkCallback(customerId, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419, "The user is not authorized to perform this action.", "User is the primary owner, access can not be revoked.")));
                }
            } else {
                if (customerId.equals(requesterId)) {
                    removeAccountLink(accountNumber, customerId);
                    sendRemoveAccountLinkCallback(customerId, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419, "The user is not authorized to perform this action.")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
        } catch (AccountDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "The provided account does not appear to exist.")));
        }
    }

    private void removeAccountLink(final String accountNumber, final String customerId) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement removeAccountLink;
        removeAccountLink = databaseConnection.getConnection().prepareStatement(removeCustomerAccountLink);
        removeAccountLink.setLong(1, Long.parseLong(customerId));
        removeAccountLink.setString(2, accountNumber);
        removeAccountLink.executeUpdate();
        removeAccountLink.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    private void sendRemoveAccountLinkCallback(final String customerId, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account link removal successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", customerId)));
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
                throw new CustomerDoesNotExistException("Customer with the given customerId does not appear to exist.");
            } else {
                Customer accountOwner = getCustomerData(customerId);
                accountOwner.setAccount(new Account(accountOwner.getInitials()
                                                    + accountOwner.getSurname(), 0.0, 0.0, false, 0.0));
                doNewAccountRequest(accountOwner, callbackBuilder);
            }
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
        } catch (CustomerDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
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
    private void verifyAccountRemovalInput(final String accountNumber, final long customerId,
                                           final CallbackBuilder callbackBuilder) {
        try {
            if (!getCustomerExistence(customerId)) {
                throw new AccountDoesNotExistException("Customer with given customerId does not appear to exist.");
            } else if (!isCustomerPrimaryOwner(accountNumber, customerId)) {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419, "The user is not authorized to perform this action.", "Account removal failed, this customer is not the primary account owner.")));
            } else {
                doAccountRemovalRequest(accountNumber, customerId, callbackBuilder);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
        } catch (AccountDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", e.getMessage())));
        }
    }

    /**
     * Removes all account links between users and the account that is being removed.
     * @param accountNumber AccountNumber of the account linked to the customer.
     * @throws SQLException Thrown when the removal query fails.
     */
    private void removeAccountLinks(final String accountNumber) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement removeAccountLink = databaseConnection.getConnection()
                                                                .prepareStatement(SQLStatements.removeAccountLinks);
        removeAccountLink.setString(1, accountNumber);
        removeAccountLink.execute();
        removeAccountLink.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Forwards an account removal request to the ledger, and if the account removal is successful removes all account
     * links for the account from the accounts database and sends a successful callback to the request source. If the
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
                MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(jsonReply), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    try {
                        removeAccountLinks(accountNumber);
                        checkIfCustomerOwnsAccounts(customerId, callbackBuilder);
                    } catch (SQLException e) {
                        System.out.printf("%s Failed to remove accountLink, sending rejection.\n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
                    }
                } else {
                    callbackBuilder.build().reply(jsonReply);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Checks if the customer still owns accounts in the system, and if the customer does not the customer is
     * removed from the system.
     * @param customerId Id of the customer to check accounts for.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void checkIfCustomerOwnsAccounts(final Long customerId, CallbackBuilder callbackBuilder) {
        try {
            SQLConnection con = databaseConnectionPool.getConnection();
            PreparedStatement ps = con.getConnection().prepareStatement(SQLStatements.getPrimaryAccountNumbersCount);
            ps.setLong(1, customerId);
            ResultSet rs = ps.executeQuery();

            boolean removedCustomer = false;
            if (rs.next() && rs.getInt(1) < 1) {
                removeCustomer(customerId);
                removedCustomer = true;
            }

            rs.close();
            ps.close();
            databaseConnectionPool.returnConnection(con);

            sendAccountRemovalCallback(removedCustomer, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
        }
    }

    /**
     * Removes a customer, and any remaining accountLinks from the users database.
     * @param customerId Id of the customer to remove.
     */
    private void removeCustomer(final Long customerId) {
        try {
            SQLConnection con = databaseConnectionPool.getConnection();

            PreparedStatement ps = con.getConnection().prepareStatement(SQLStatements.removeCustomer);
            ps.setLong(1, customerId);
            ps.executeUpdate();

            ps = con.getConnection().prepareStatement(SQLStatements.removeCustomerLinks);
            ps.setLong(1, customerId);
            ps.executeUpdate();

            ps.close();
            databaseConnectionPool.returnConnection(con);
        } catch (SQLException e) {
            System.out.printf("%s Failed to remove customer from system", PREFIX);
        }
    }

    private void sendAccountRemovalCallback(final boolean removedCustomer, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account removal successful, sending callback.\n", PREFIX);
        CloseAccountReply reply = new CloseAccountReply(removedCustomer, true, "");
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply", reply)));
    }

    @RequestMapping(value = "/transferBankAccount", method = RequestMethod.PUT)
    public void processTransferBankAccountRequest(final Callback<String> callback,
                                          final @RequestParam("request") String accountLinkRequestJson) {
        System.out.printf("%s Received bank account transfer request.\n", PREFIX);
        AccountLink accountLink = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        long customerId = accountLink.getCustomerId();
        String accountNumber = accountLink.getAccountNumber();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleTransferBankAccountExceptions(accountNumber, customerId, callbackBuilder);
    }

    private void handleTransferBankAccountExceptions(
            final String accountNumber, final long customerId, final CallbackBuilder callbackBuilder) {
        try {
            Customer customer = getCustomerData(customerId);
            customer.setAccount(new Account(accountNumber));
            doTransferBankAccountRequest(customer, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
        } catch (CustomerDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.", "The provided customer does not appear to exist.")));
        }
    }

    private void doTransferBankAccountRequest(final Customer customer, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/transferBankAccount",
                "customer", jsonConverter.toJson(customer), (httpStatusCode, httpContentType, jsonReply) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(jsonReply), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            try {
                                transferAccountAccess(customer.getAccount().getAccountNumber(), customer.getCustomerId());
                                sendTransferBankAccountCallback(callbackBuilder);
                            } catch (SQLException e) {
                                System.out.printf("%s Failed to transfer bank account, sending rejection.\n", PREFIX);
                                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Error connecting to Users database.")));
                            }
                        } else {
                            callbackBuilder.build().reply(jsonReply);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void transferAccountAccess(final String accountNumber, final long customerId) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement ps1;
        PreparedStatement ps2;
        ps1 = databaseConnection.getConnection().prepareStatement(revokeBankAccountAccess);
        ps2 = databaseConnection.getConnection().prepareStatement(transferBankAccountAccess);
        ps1.setLong(1, customerId);
        ps1.setString(2, accountNumber);
        ps2.setLong(1, customerId);
        ps2.setString(2, accountNumber);
        ps1.executeUpdate();
        ps2.executeUpdate();
        ps1.close();
        ps2.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    private void sendTransferBankAccountCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Bank Account transfer successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
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
