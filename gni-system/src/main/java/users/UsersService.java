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
        Long customerId = dataRequest.getCustomerId();
        if (dataRequest.getType() == RequestType.ACCOUNTS) {
            DataReply accountData = new DataReply();
            accountData.setType(RequestType.ACCOUNTS);
            accountData.setAccountNumbers(getAccountNumbers(customerId));
            callbackBuilder.build().reply(jsonConverter.toJson(accountData));
            System.out.println("Users: Sent reply back to UI.");
        } else {
            //The request is a Customer Data request so fetch this and return it.
            Customer customerData = getCustomerData(customerId);
            System.out.println("Users: Sending data back to UI.");
            callbackBuilder.build().reply(jsonConverter.toJson(customerData));
        }
    }

    /**
     * Fetches account numbers from the accounts table for the customer with the respective id, returns this in a
     * list object.
     * @param customerId Customer id of the customer we want to fetch accounts for.
     * @return List containing account number that belong to the customer, null if no account numbers belong to the
     *         customer.
     */
    private List<String> getAccountNumbers(final Long customerId) {
        try {
            List<String> accountNumbersOfCustomer = new ArrayList<>();
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement fetchAccountNumbers = databaseConnection.getConnection()
                                                                                .prepareStatement(getAccountNumbers);
            fetchAccountNumbers.setLong(1, customerId);
            ResultSet linkedAccountNumbers = fetchAccountNumbers.executeQuery();
            while (linkedAccountNumbers.next()) {
                accountNumbersOfCustomer.add(linkedAccountNumbers.getString("account_number"));
            }
            fetchAccountNumbers.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            if (accountNumbersOfCustomer.isEmpty()) {
                return null;
            }
            return accountNumbersOfCustomer;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Fetches customer data from the customers table for the customer with id customerId and returns
     * this data in a Customer object.
     * @param customerId Id of the customer to fetch data for.
     * @return Customer object containing the data for Customer with id=customerId
     */
    private Customer getCustomerData(final Long customerId) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement fetchCustomerData = databaseConnection.getConnection().prepareStatement(getUserInformation);
            fetchCustomerData.setLong(1, customerId);
            ResultSet customerData = fetchCustomerData.executeQuery();
            if (customerData.next()) {
                Customer customer = new Customer();
                customer.setInitials(customerData.getString("initials"));
                customer.setName(customerData.getString("firstname"));
                customer.setSurname(customerData.getString("lastname"));
                customer.setEmail(customerData.getString("email"));
                customer.setTelephoneNumber(customerData.getString("telephone_number"));
                customer.setAddress(customerData.getString("address"));
                customer.setDob(customerData.getString("date_of_birth"));
                customer.setSsn(customerData.getLong("social_security_number"));
                fetchCustomerData.close();
                databaseConnectionPool.returnConnection(databaseConnection);
                return customer;
            } else {
                fetchCustomerData.close();
                databaseConnectionPool.returnConnection(databaseConnection);
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sends a data request to the LedgerService and handles the response from the ledger.
     * Uses the callbackBuilder to send the reply from the ledger back to the UI service.
     * @param dataRequest DataRequest that was sent to the ledger {@link DataRequest}.
     * @param callbackBuilder Used to send the reply of the ledger back to the UI service.
     */
    //todo forward instantly instead of converting to/from json
    private void doLedgerDataRequest(final DataRequest dataRequest, final CallbackBuilder callbackBuilder) {
        RequestType dataRequestType = dataRequest.getType();
        if (dataRequestType == RequestType.BALANCE || dataRequestType == RequestType.TRANSACTIONHISTORY) {
            ledgerClient.getAsyncWith1Param("/services/ledger/data", "body",
                                            jsonConverter.toJson(dataRequest),
                                            (httpStatusCode, httpContentType, replyJson) -> {
                if (httpStatusCode == HTTP_OK) {
                    DataReply ledgerReply = jsonConverter.fromJson(replyJson.substring(1, replyJson.length() - 1)
                                                                            .replaceAll("\\\\", ""), DataReply.class);
                    callbackBuilder.build().reply(jsonConverter.toJson(ledgerReply));
                } else {
                    callbackBuilder.build().reject("Recieved an error from ledger.");
                }
            });
        } else {
            callbackBuilder.build().reject("Received a request of unknown type.");
        }
    }

    /**
     * Processes transaction requests coming from the UI service by forwarding them to the TransactionDispatch service.
     * @param callback Used to send the result back to the UI service.
     * @param requestJson Json String containing a Transaction object for a transaction request.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          final @RequestParam("body") String requestJson) {
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
                processTransactionReply(replyJson, transactionRequest, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Couldn't reach transactionDispatch.");
            }
        });
    }

    private void processTransactionReply(final String replyJson, final Transaction transactionRequest,
                                         final CallbackBuilder callbackBuilder) {
        Transaction transactionReply = jsonConverter.fromJson(replyJson.substring(1, replyJson.length() - 1)
                                                                                    .replaceAll("\\\\", ""),
                                                                                    Transaction.class);
        if (transactionReply.isProcessed() && transactionReply.equalsRequest(transactionRequest)) {
            if (transactionReply.isSuccessful()) {
                System.out.println("Users: Transaction was successfull");
                callbackBuilder.build().reply(jsonConverter.toJson(transactionReply));
            } else {
                callbackBuilder.build().reject("Transaction was unsuccessfull.");
            }
        } else {
            callbackBuilder.build().reject("Transaction couldn't be processed.");
        }
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
        Customer customerRequest = jsonConverter.fromJson(requestJson, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountNumberRequest(customerRequest, callbackBuilder);
    }

    /**
     * Sends request for obtaining an accountNumber to the ledger, then processes the customer request internally in
     * the User database and sends a reply back to the UI service.
     * @param customerRequest Customer object that was used to make a new customer request.
     * @param callbackBuilder Used to send the result of the customer request back to the UI service.
     */
    //todo rename to a more appropriate function name, split up into more uniform functions
    private void doAccountNumberRequest(final Customer customerRequest, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body",
                                            jsonConverter.toJson(customerRequest.getAccount()),
                                            (httpStatusCode, httpContentType, replyAccountJson) -> {
            if (httpStatusCode == HTTP_OK) {
                Account accountAssignedByLedger = jsonConverter.fromJson(replyAccountJson.substring(1,
                                                                        replyAccountJson.length() - 1)
                                                                        .replaceAll("\\\\", ""), Account.class);
                customerRequest.setAccount(accountAssignedByLedger);
                enrollCustomer(customerRequest, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Recieved an error from ledger.");
            }
        });
    }

    /**
     * Enrolls the customer in the Users database.
     * @param customer Customer to enroll in the database.
     * @param callbackBuilder Used to send the outcome to the UIService.
     */
    private void enrollCustomer(final Customer customer, final CallbackBuilder callbackBuilder) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            Long newID = databaseConnection.getNextID(getNextUserID);
            PreparedStatement createNewCustomer = databaseConnection.getConnection().prepareStatement(createNewUser);
            createNewCustomer.setLong(1, newID);                           // id
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
            boolean accountLinkedToCustomer = addAccountToCustomer(newID, customer.getAccount().getAccountNumber());
            if (accountLinkedToCustomer) {
                System.out.printf("Users: Added users %s %s to the customer database\n",
                                                            customer.getName(), customer.getSurname());
                callbackBuilder.build().reply(jsonConverter.toJson(customer));
            } else {
                callbackBuilder.build().reject("SQLException when adding account to accounts table.");
            }
        } catch (SQLException e) {
            callbackBuilder.build().reject(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Links an accountNumber to a Customer in the Customers database by inserting the customerID and the accountnumber
     * into the accounts table.
     * @param customerId Id of the customer to link the account to.
     * @param accountNumber Account number to link to the customer.
     * @return boolean indicating if the account is successfully linked to the customer.
     */
    private boolean addAccountToCustomer(final Long customerId, final String accountNumber) {
        try {
            boolean linkExists = false;
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement fetchAccountNumbers = databaseConnection.getConnection()
                                                                                .prepareStatement(getAccountNumbers);
            fetchAccountNumbers.setLong(1, customerId);
            ResultSet linkedAccountNumbers = fetchAccountNumbers.executeQuery();
            while (linkedAccountNumbers.next() && !linkExists) {
                if (linkedAccountNumbers.getString("account_number").equals(accountNumber.trim())) {
                    linkExists = true;
                }
            }
            fetchAccountNumbers.close();
            if (linkExists) {
                System.out.println("Users: Account link already exists.");
                return true;
            }
            PreparedStatement linkAccountToCustomer = databaseConnection.getConnection()
                                                                                    .prepareStatement(addAccountToUser);
            linkAccountToCustomer.setLong(1, customerId);
            linkAccountToCustomer.setString(2, accountNumber);
            linkAccountToCustomer.executeUpdate();
            linkAccountToCustomer.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            System.out.printf("Users: Added Accountnumber %s to userid %d\n", accountNumber, customerId);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Takes an account link request and links the account to the specified customer id.
     * @param callback Used to send the result back to the UI Service.
     * @param body Json string representing an AccountLink{@link AccountLink} object containing
     *             an account number which is to be attached to a customer with the specified customerId.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void linkAccount(final Callback<String> callback, final @RequestParam("body") String body) {
        AccountLink accountLink = jsonConverter.fromJson(body, AccountLink.class);
        Long customerId = accountLink.getCustomerId();
        String accountNumber = accountLink.getAccountNumber();
        if (customerId == null || customerId < 0 || accountNumber == null) {
            callback.reject("CustomerId or AccountNumber not specified.");
        }
        DataRequest accountExistsRequest = JSONParser.createAccountExistsRequest(accountNumber);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountLink(accountExistsRequest, customerId, callbackBuilder);
    }

    /**
     * Checks if an account exists in the ledger, if it does this is an existing account that will be added to the
     * customer specified in request. Otherwise the request will be rejected.
     * @param accountLinkRequest DataRequest object containing an account link request.
     * @param customerId Id of the customer to link the account to.
     * @param callbackBuilder Used to send a callback containing the result to UI.
     */
    private void doAccountLink(final DataRequest accountLinkRequest, final Long customerId,
                                                                            final CallbackBuilder callbackBuilder) {
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "body",
                                                                    jsonConverter.toJson(accountLinkRequest),
                                                                    (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                DataReply ledgerReply = jsonConverter.fromJson(replyJson.substring(1, replyJson.length() - 1)
                                                                            .replaceAll("\\\\", ""), DataReply.class);
                if (ledgerReply.isAccountInLedger()) {
                    if (customerExists(customerId)) {
                        boolean accountLinkedToCustomer = addAccountToCustomer(customerId,
                                                                               accountLinkRequest.getAccountNumber());
                        AccountLink reply = new AccountLink(customerId, accountLinkRequest.getAccountNumber(),
                                                            accountLinkedToCustomer);
                        System.out.println("Users: Account link successfull.");
                        callbackBuilder.build().reply(jsonConverter.toJson(reply));
                    } else {
                        System.out.println("Users: Account link failed, UserId does not exist.");
                        callbackBuilder.build().reject("UserId does not exist.");
                    }
                } else {
                    System.out.println("Users: Account link failed, account does not exist.");
                    callbackBuilder.build().reject("Account does not exist.");
                }
            } else {
                System.out.println("Users: Unsuccessfull ledger call.");
                callbackBuilder.build().reject("Unsuccessfull call, code: " + httpStatusCode);
            }
        });
    }

    /**
     * Checks if a customer with id customerId exists in the users database.
     * @param customerId Id of the customer to look for.
     * @return Boolean indicating if the customer exists in the users database.
     */
    private boolean customerExists(final Long customerId) {
        boolean customerExists = false;
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement fetchCustomerCount = databaseConnection.getConnection().prepareStatement(getUserCount);
            fetchCustomerCount.setLong(1, customerId);
            ResultSet customerCount = fetchCustomerCount.executeQuery();
            if (customerCount.next()) {
                // check if the amount of customers with the id is larger than 0
                if (customerCount.getLong(1) > 0) {
                    customerExists = true;
                }
            }
            customerCount.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return customerExists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    //todo refacter so that creation of a new customer isnt requested with an accountlink request.
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void createNewCustomerAccount(final Callback<String> callback,
                                         final @RequestParam("body") String requestJson) {
        System.out.println("Users: Received account creation request.");
        AccountLink request = jsonConverter.fromJson(requestJson, AccountLink.class);
        Long customerId = request.getCustomerId();
        if (customerId == null || customerId < 0) {
            callback.reject("CustomerId not specified.");
        }
        Customer customer = getCustomerData(customerId);
        if (customer != null) {
            final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
            doAccountCreation(request, customer, callbackBuilder);
        } else {
            callback.reject("Customer does not exist.");
        }
    }

    private void doAccountCreation(final AccountLink accountLink, final Customer customer,
                                                                            final CallbackBuilder callbackBuilder) {
        String accountHolderName = customer.getInitials() + customer.getSurname();
        Account account = JSONParser.createJsonAccount(0, 0, accountHolderName);
        ledgerClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body",
                                                                jsonConverter.toJson(account), (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Account ledgerReply = jsonConverter.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                                                                                        Account.class);
                if (ledgerReply.getAccountNumber() != null && ledgerReply.getAccountHolderName() != null &&
                        ledgerReply.getAccountHolderName().equals(accountHolderName)) {
                    //Ledger successfully generated a new account for our user, create the link in the users databaseConnectionPool.
                    boolean accountLinked = addAccountToCustomer(accountLink.getCustomerId(),
                                                                                    ledgerReply.getAccountNumber());
                    accountLink.setAccountNumber(ledgerReply.getAccountNumber());
                    accountLink.setSuccessfull(accountLinked);
                    callbackBuilder.build().reply(jsonConverter.toJson(accountLink));
                    System.out.println("Users: Successfully created account.");
                } else {
                    System.out.println("Users: Failed to create account.");
                    callbackBuilder.build().reject("Reply from the ledger contained incorrect data.");
                }
            } else {
                System.out.println("Users: Failed to create account.");
                callbackBuilder.build().reject("Could not reach ledger.");
            }

        });
    }
}
