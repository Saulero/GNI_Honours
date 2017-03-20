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
    private ConnectionPool db;

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
        this.db = new ConnectionPool();
    }

    /**
     * Processes incoming data requests from the UI service and sends a reply back through a callback, if necessary
     * sends the request to the LedgerService service and waits for a callback from the LedgerService.
     * @param callback Used to send result back to the UI service.
     * @param body Json String representing a DataRequest that is made by the UI service {@link DataRequest}.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body, DataRequest.class);
        RequestType type = request.getType();
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        if (type == RequestType.CUSTOMERDATA || type == RequestType.ACCOUNTS) {
            System.out.println("Users: Called by UI, fetching customer data.");
            Long userId = request.getUserId();
            if (type == RequestType.ACCOUNTS) {
                DataReply reply = new DataReply();
                List<String> accountNumbers = getAccountNumbers(userId);
                reply.setType(request.getType());
                reply.setAccountNumbers(accountNumbers);
                callbackBuilder.build().reply(gson.toJson(reply));
                System.out.println("Users: Sent reply back to UI.");
            } else {
                Customer reply = getCustomerData(userId);
                System.out.println("Users: Sending data back to UI.");
                callbackBuilder.build().reply(gson.toJson(reply));
            }
        } else {
            System.out.println("Users: Called by UI, calling Ledger");
            doDataRequest(request, gson, callbackBuilder);
        }
    }

    /**
     * Fetches account numbers from the accounts table for the user with id userId, returns this in a list object.
     * @param userId User id of the customer we want to fetch accounts for.
     * @return List containing account number that belong to the customer, null if no account numbers belong to the
     * customer.
     */
    private List<String> getAccountNumbers(final Long userId) {
        try {
            List<String> reply = new ArrayList<>();
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(getAccountNumbers);
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                reply.add(rs.getString("account_number"));
            }
            ps.close();
            db.returnConnection(connection);
            if (reply.isEmpty()) {
                return null;
            }
            return reply;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Fetches customer data from the users table for the user with id userId and returns
     * this data in a Customer object.
     * @param userId Id of the user to fetch data for.
     * @return Customer object containing the data for Customer with id=userId
     */
    private Customer getCustomerData(final Long userId) {
        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(getUserInformation);
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Customer reply = new Customer();
                reply.setInitials(rs.getString("initials"));
                reply.setName(rs.getString("firstname"));
                reply.setSurname(rs.getString("lastname"));
                reply.setEmail(rs.getString("email"));
                reply.setTelephoneNumber(rs.getString("telephone_number"));
                reply.setAddress(rs.getString("address"));
                reply.setDob(rs.getString("date_of_birth"));
                reply.setSsn(rs.getLong("social_security_number"));
                ps.close();
                db.returnConnection(connection);
                return reply;
            } else {
                ps.close();
                db.returnConnection(connection);
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
     * @param request DataRequest that was sent to the ledger {@link DataRequest}.
     * @param gson Used for Json conversions.
     * @param callbackBuilder Used to send the reply of the ledger back to the UI service.
     */
    private void doDataRequest(final DataRequest request, final Gson gson, final CallbackBuilder callbackBuilder) {
        if (request.getType() == RequestType.BALANCE || request.getType() == RequestType.TRANSACTIONHISTORY) {
            ledgerClient.getAsyncWith1Param("/services/ledger/data", "body", gson.toJson(request),
                    (code, contentType, replyBody) -> {
                if (code == HTTP_OK) {
                    DataReply reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                                    .replaceAll("\\\\", ""), DataReply.class);
                    callbackBuilder.build().reply(gson.toJson(reply));
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
     * @param body Json String containing a Transaction object for a transaction request.
     */
    //TODO handle reply from TransactionDispatch service
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction request = gson.fromJson(body, Transaction.class);
        //TODO send transaction to TransactionDispatch service
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        System.out.println("Users: Sent transaction to TransactionDispatch");
        doTransactionRequest(request, gson, callbackBuilder);
    }

    /**
     * Sends transaction request to the TransactionDispatch service and sends the reply back to the UI service.
     * @param request Transaction request made by the UI service {@link Transaction}.
     * @param gson Used to do Json conversions.
     * @param callbackBuilder Used to send the result back to the UI service.
     */
    private void doTransactionRequest(final Transaction request, final Gson gson,
                                      final CallbackBuilder callbackBuilder) {
        transactionDispatchClient.putFormAsyncWith1Param("/services/transactionDispatch/transaction",
                "body", gson.toJson(request), (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Transaction reply = gson.fromJson(body.substring(1, body.length() - 1)
                                                                .replaceAll("\\\\", ""), Transaction.class);
                if (reply.isProcessed() && reply.equalsRequest(request)) {
                    if (reply.isSuccessful()) {
                        System.out.println("Users: Transaction was successfull");
                        callbackBuilder.build().reply(gson.toJson(reply));
                    } else {
                        callbackBuilder.build().reject("Transaction was unsuccessfull.");
                    }
                } else {
                    callbackBuilder.build().reject("Transaction couldn't be processed.");
                }
            } else {
                callbackBuilder.build().reject("Couldn't reach transactionDispatch.");
            }
        });
    }

    /**
     * Processes customer creation requests coming from the UI service, sends the request to the LedgerService service to
     * obtain an accountNumber for the customer and then processes the customer in the User database.
     * @param callback Used to send the result of the request back to the UI service.
     * @param body Json string containing the Customer object the request is for {@link Customer}.
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    //todo rewrite so we can use initials + surname for accountholdername.
    public void processNewCustomer(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Customer customer = gson.fromJson(body, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountNumberRequest(gson, customer, callbackBuilder);
    }

    /**
     * Sends request for obtaining an accountNumber to the ledger, then processes the customer request internally in
     * the User database and sends a reply back to the UI service.
     * @param gson Used to do Json conversions.
     * @param customer Customer object that was used to make a new customer request.
     * @param callbackBuilder Used to send the result of the customer request back to the UI service.
     */
    private void doAccountNumberRequest(final Gson gson, final Customer customer,
                                        final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body",
                                        gson.toJson(customer.getAccount()), (code, contentType, replyBody) -> {
                if (code == HTTP_OK) {
                    Account ledgerReply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                            .replaceAll("\\\\", ""), Account.class);
                    customer.setAccount(ledgerReply);

                    enrollCustomer(customer, callbackBuilder, gson);
                } else {
                    callbackBuilder.build().reject("Recieved an error from ledger.");
                }
            });
    }

    /**
     * Enrolls the customer in the Users database.
     * @param customer Customer to enroll in the database.
     * @param callbackBuilder Used to send the outcome to the UIService.
     * @param gson Used for Json conversions.
     */
    private void enrollCustomer(final Customer customer, final CallbackBuilder callbackBuilder, final Gson gson) {
        try {
            SQLConnection connection = db.getConnection();
            Long newID = connection.getNextID(getNextUserID);
            PreparedStatement ps = connection.getConnection().prepareStatement(createNewUser);
            ps.setLong(1, newID);                           // id
            ps.setString(2, customer.getInitials());        // initials
            ps.setString(3, customer.getName());            // firstname
            ps.setString(4, customer.getSurname());         // lastname
            ps.setString(5, customer.getEmail());           // email
            ps.setString(6, customer.getTelephoneNumber()); //telephone_number
            ps.setString(7, customer.getAddress());         //address
            ps.setString(8, customer.getDob());             //date_of_birth
            ps.setLong(9, customer.getSsn());               //social_security_number
            ps.executeUpdate();
            ps.close();
            db.returnConnection(connection);
            System.out.printf("Users: Added users %s %s to the customer database\n",
                    customer.getName(), customer.getSurname());
            boolean addedAccount = addAccountToCustomerDb(newID, customer.getAccount().getAccountNumber());
            if (addedAccount) {
                callbackBuilder.build().reply(gson.toJson(customer));
            } else {
                callbackBuilder.build().reject("SQLException when adding account to accounts table.");
            }
        } catch (SQLException e) {
            callbackBuilder.build().reject(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Links an account to a user in the Users database by inserting the userID and the accountnumber into the
     * accounts table.
     * @param customerId Id of the customer to link the account to.
     * @param accountNumber Account number to link to the customer.
     * @return boolean indicating if the account is successfully linked to the customer.
     */
    private boolean addAccountToCustomerDb(final Long customerId, final String accountNumber) {
        try {
            boolean linkExists = false;
            SQLConnection connection = db.getConnection();
            PreparedStatement check = connection.getConnection().prepareStatement(getAccountNumbers);
            check.setLong(1, customerId);
            ResultSet rs = check.executeQuery();
            while (rs.next()) {
                if (rs.getString("account_number").equals(accountNumber.trim())) {
                    linkExists = true;
                }
            }
            check.close();
            if (linkExists) {
                System.out.println("Users: Account link already exists.");
                return true;
            }
            PreparedStatement ps = connection.getConnection().prepareStatement(addAccountToUser);
            ps.setLong(1, customerId);
            ps.setString(2, accountNumber);
            ps.executeUpdate();
            ps.close();
            db.returnConnection(connection);
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
    public void linkCustomerAccount(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        AccountLink request = gson.fromJson(body, AccountLink.class);
        Long customerId = request.getCustomerId();
        String accountNumber = request.getAccountNumber();
        if (customerId == null || customerId < 0 || accountNumber == null) {
            callback.reject("CustomerId or AccountNumber not specified.");
        }
        DataRequest dataRequest = JSONParser.createAccountExistsRequest(accountNumber);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doAccountLink(gson, dataRequest, customerId, callbackBuilder);
    }

    /**
     * Checks if an account exists in the ledger, if it does this is an existing account that will be added to the
     * user specified in request. Otherwise the request will be rejected.
     * @param gson Used for json conversions.
     * @param request DataRequest object containing an account link request.
     * @param callbackBuilder Used to send a callback to UI.
     */
    private void doAccountLink(final Gson gson, final DataRequest request, final Long customerId,
                               final CallbackBuilder callbackBuilder) {
        ledgerClient.getAsyncWith1Param("/services/ledger/data", "body",
                                                            gson.toJson(request), (code, contentType, replyBody) -> {
            if (code == HTTP_OK) {
                DataReply ledgerReply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                        .replaceAll("\\\\", ""), DataReply.class);
                if (ledgerReply.isAccountInLedger()) {
                    if (customerExists(customerId)) {
                        boolean addedAccount = addAccountToCustomerDb(customerId, request.getAccountNumber());
                        AccountLink reply = new AccountLink(customerId, request.getAccountNumber(),
                                addedAccount);
                        System.out.println("Users: Account link successfull.");
                        callbackBuilder.build().reply(gson.toJson(reply));
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
                callbackBuilder.build().reject("Unsuccessfull call, code: " + code);
            }
        });
    }

    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void createNewCustomerAccount(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        AccountLink request = gson.fromJson(body, AccountLink.class);
        Long customerId = request.getCustomerId();
        if (customerId == null || customerId < 0) {
            callback.reject("CustomerId not specified.");
        }
        Customer customer = getCustomerData(customerId);
        if (customer != null) {
            final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);

        } else {
            callback.reject("Customer does not exist.");
        }
    }

    private void doAccountCreation(final Gson gson, final AccountLink accountLink, final Customer customer,
                                                                            final CallbackBuilder callbackBuilder) {
        String accountHolderName = customer.getInitials() + customer.getSurname();
        System.out.println("accountholdername: " + accountHolderName);
        Account account = JSONParser.createJsonAccount(0, 0, accountHolderName);
        ledgerClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body",
                                                                gson.toJson(account), (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Account ledgerReply = gson.fromJson(body, Account.class);
                if (ledgerReply.getAccountNumber() != null && ledgerReply.getAccountHolderName() != null &&
                        ledgerReply.getAccountHolderName().equals(accountHolderName)) {
                    //Ledger successfully generated a new account for our user, create the link in the users db.
                    boolean accountLinked = addAccountToCustomerDb(accountLink.getCustomerId(),
                                                                                    ledgerReply.getAccountNumber());
                    accountLink.setAccountNumber(ledgerReply.getAccountNumber());
                    accountLink.setSuccessfull(accountLinked);
                    callbackBuilder.build().reply(gson.toJson(accountLink));
                } else {
                    callbackBuilder.build().reject("Reply from the ledger contained incorrect data.");
                }
            } else {
                callbackBuilder.build().reject("Could not reach ledger.");
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
            SQLConnection connection = db.getConnection();
            PreparedStatement check = connection.getConnection().prepareStatement(getUserCount);
            check.setLong(1, customerId);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                Long customerCount = rs.getLong(1);
                if (customerCount > 0) {
                    customerExists = true;
                }
            }
            rs.close();
            db.returnConnection(connection);
            return customerExists;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
