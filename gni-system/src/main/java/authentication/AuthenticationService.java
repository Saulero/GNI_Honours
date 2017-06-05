package authentication;

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
import jdk.nashorn.internal.codegen.CompilerConstants;
import users.CustomerDoesNotExistException;
import util.JSONParser;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import static database.SQLStatements.*;
import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul & Noel
 * @version 2
 */
@RequestMapping("/authentication")
class AuthenticationService {
    /** Connection to the users service. */
    private HttpClient usersClient;
    /** Connection to the pin service. */
    private HttpClient pinClient;
    /** Database connection pool containing persistent database connections. */
    private ConnectionPool databaseConnectionPool;
    /** Secure Random Number Generator. */
    private SecureRandom secureRandomNumberGenerator;
    /** Used for Json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Authentication Service. */
    private static final String PREFIX = "[Auth]                :";

    /**
     * Constructor.
     * @param usersPort port on which the Users service can be found.
     * @param usersHost Host on which the Users service can be found.
     */
    AuthenticationService(final int usersPort, final String usersHost, final int pinPort, final String pinHost) {
        this.usersClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).buildAndStart();
        this.pinClient = httpClientBuilder().setHost(pinHost).setPort(pinPort).buildAndStart();
        this.databaseConnectionPool = new ConnectionPool();
        this.secureRandomNumberGenerator = new SecureRandom();
        this.jsonConverter = new Gson();
    }

    /**
     * Creates a callback for the data request and then calls the exception handler.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param dataRequestJson Json string representing a dataRequest object.
     * @param cookie Cookie of the person performing the dataRequest, used to check if the request is allowed.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   @RequestParam("request") final String dataRequestJson,
                                   @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleDataRequestExceptions(dataRequestJson, cookie, callbackBuilder);
    }

    /**
     * Authenticates a data request and then forwards the data request to the Users service.
     * @param dataRequestJson Json String representing the data request.
     * @param cookie Cookie of the customer that sent the request, used to authenticate the request.
     * @param callbackBuilder Used to send a callback to the service that sent the request.
     */
    private void handleDataRequestExceptions(final String dataRequestJson, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            DataRequest dataRequest = jsonConverter.fromJson(dataRequestJson, DataRequest.class);
            dataRequest.setCustomerId(getCustomerId(cookie));
            doDataRequest(jsonConverter.toJson(dataRequest), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Failed to query database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("CookieData does not belong to an authorized user.");
        }
    }

    /**
     * Checks if a request is authorized by checking if the token in the cookie is the correct token for that customer
     * and the token is still valid if one of these conditions is not met a UserNotAuthorizedException is thrown.
     * @param cookie Cookie of a customer
     * @throws UserNotAuthorizedException thrown when the token is not legitimate/expired or the userId does not exist.
     * @throws SQLException thrown when there is a problem fetching the authentication data from the database.
     */
    void authenticateRequest(final String cookie) throws UserNotAuthorizedException, SQLException {
        Long[] cookieData = decodeCookie(cookie);
        long customerId = cookieData[0];
        long cookieToken = cookieData[1];
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getAuthenticationData = databaseConnection.getConnection()
                                                                    .prepareStatement(getAuthenticationData2);
        getAuthenticationData.setLong(1, customerId);
        ResultSet authenticationData = getAuthenticationData.executeQuery();
        if (authenticationData.next()) {
            long customerToken = authenticationData.getLong("token");
            long tokenValidity = authenticationData.getLong("token_validity");
            if (cookieToken == customerToken && System.currentTimeMillis() < tokenValidity) {
                updateTokenValidity(customerId);
            } else {
                authenticationData.close();
                getAuthenticationData.close();
                databaseConnectionPool.returnConnection(databaseConnection);
                throw new UserNotAuthorizedException("Token not legitimate or expired.");
            }
        } else {
            authenticationData.close();
            getAuthenticationData.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            throw new UserNotAuthorizedException("UserId not found.");
        }
        authenticationData.close();
        getAuthenticationData.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Decodes a cookie into an array with the data of the cookie inside of it.
     * @param cookie Cookie String to convert to its data.
     * @return Long[] containing in index 0 the customerId of the customer and in index 1 the token of the customer.
     */
    Long[] decodeCookie(final String cookie) {
        String[] cookieParts = cookie.split(":");
        Long[] cookieData = new Long[2];
        cookieData[0] = Long.parseLong(cookieParts[0]); //customerId
        cookieData[1] = Long.parseLong(new String(Base64.getDecoder().decode(cookieParts[1].getBytes()))); //token
        return cookieData;
    }

    /**
     * Updates the validity of the token upon reuse.
     * @param id The user_id of the row to update
     */
    void updateTokenValidity(final long id) {
        long validity = System.currentTimeMillis() + Variables.TOKEN_VALIDITY * 1000;
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateTokenValidity);
            ps.setLong(1, validity);    // validity
            ps.setLong(2, id);          // id
            ps.executeUpdate();

            ps.close();
            databaseConnectionPool.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetches the customerId from a cookie.
     * @param cookie Cookie of a customer.
     * @return the id of the customer the cookie belongs to.
     */
    Long getCustomerId(final String cookie) {
        Long[] cookieData = decodeCookie(cookie);
        return cookieData[0];
    }

    /**
     * Forwards the data request to the Users service and sends the reply off to processing, or rejects the request if
     * the forward fails.
     * @param dataRequestJson Json string representing a dataRequest that should be sent to the UsersService.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doDataRequest(final String dataRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding data request.\n", PREFIX);
        usersClient.getAsyncWith1Param("/services/users/data", "request",
                                        dataRequestJson, (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                handleDataReply(dataReplyJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Transaction history request failed.");
            }
        });
    }

    private void handleDataReply(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        DataReply reply = jsonConverter.fromJson(dataReplyJson, DataReply.class);
        if (reply.getType() == RequestType.OWNERS) {
            for (AccountLink link : reply.getAccounts()) {
                link.setUsername(getUserNameFromCustomerId(link.getCustomerId()));
            }
            reply.setType(RequestType.ACCOUNTS);
            sendDataRequestCallback(jsonConverter.toJson(reply), callbackBuilder);
        } else {
            sendDataRequestCallback(JSONParser.removeEscapeCharacters(dataReplyJson), callbackBuilder);
        }
    }

    private String getUserNameFromCustomerId(final Long customerId) {
        String username;
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement getUsername = databaseConnection.getConnection()
                                                                .prepareStatement(getUsernameFromCustomerId);
            getUsername.setLong(1, customerId);
            ResultSet usernameSet = getUsername.executeQuery();
            if (usernameSet.next()) {
                username = usernameSet.getString("username");
            } else {
                throw new CustomerDoesNotExistException("username not found");
            }
            getUsername.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return username;
        } catch (SQLException | CustomerDoesNotExistException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sends the result of a data request back to the service that requested it.
     * @param dataReplyJson Json String containing the reply that was received.
     * @param callbackBuilder Used to send back the reply to the service that requested it.
     */
    private void sendDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Data request successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(dataReplyJson);
    }

    /**
     * Creates a callback builder to forward the result of the request to the requester, and then forwards the request
     * to the Users service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param transactionRequestJson Json String representing a Transaction object that is to be processed
     *                               {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("request") final String transactionRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleTransactionRequestExceptions(transactionRequestJson, cookie, callbackBuilder);
    }

    /**
     * Checks if the request is authorized and then forwards the transaction to the TransactionDispatch service.
     * @param transactionRequestJson Json String representing a transaction request.
     * @param cookie Cookie of the customer that made the request.
     * @param callbackBuilder Used to send the reply back to the service that sent the request.
     */
    private void handleTransactionRequestExceptions(final String transactionRequestJson, final String cookie,
                                                    final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            doTransactionRequest(transactionRequestJson, getCustomerId(cookie), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Failed to query database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("CookieData does not belong to an authorized user.");
        }
    }

    /**
     * Forwards transaction request to the User service and forwards the reply or sends a rejection if the request
     * fails.
     * @param transactionRequestJson Transaction request that should be processed.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final String transactionRequestJson, final Long customerId,
                                      final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding transaction request.\n", PREFIX);
        usersClient.putFormAsyncWith2Params("/services/users/transaction", "request",
                transactionRequestJson, "customerId", customerId.toString(),
                (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendTransactionRequestCallback(transactionReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Transaction request failed.");
                    }
                });
    }

    /**
     * Forwards the result of a transaction request to the service that sent the request.
     * @param transactionReplyJson Json String representing the executed transaction {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void sendTransactionRequestCallback(final String transactionReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Transaction successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(transactionReplyJson));
    }

    /**
     * Registers a new customer into the system, includes account creation and login creation.
     * @param callback Used to send a reply back to the UserService
     * @param newCustomerRequestJson Json String representing login information
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("customer") final String newCustomerRequestJson) {
        System.out.printf("%s Registering new customer login information.\n", PREFIX);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleUsernameValidationExceptions(newCustomerRequestJson, callbackBuilder);
    }

    /**
     * Checks if the username for a new customer is a valid username, and then forwards the new customer request to
     * the Users service.
     * @param newCustomerRequestJson Json String representing a new customer request.
     * @param callbackBuilder Used to send a reply back to the requesting service.
     */
    private void handleUsernameValidationExceptions(final String newCustomerRequestJson,
                                                    final CallbackBuilder callbackBuilder) {
        try {
            validateUsername(jsonConverter.fromJson(newCustomerRequestJson, Customer.class));
            doNewCustomerRequest(newCustomerRequestJson, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UsernameTakenException e) {
            callbackBuilder.build().reject("Username taken, please choose a different username.");
        }
    }

    /**
     * Checks if the username of the customer to enroll already exists in the database.
     * @param customerToEnroll Customer of which the username should be checked.
     * @throws SQLException Thrown if something goes wrong when connecting to the database.
     * @throws UsernameTakenException Thrown if the username exists in the database.
     */
    void validateUsername(final Customer customerToEnroll) throws SQLException, UsernameTakenException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getUsernameCount = databaseConnection.getConnection()
                .prepareStatement(getLoginUsernameCount);
        getUsernameCount.setString(1, customerToEnroll.getUsername());
        ResultSet userNameOccurences = getUsernameCount.executeQuery();
        if (userNameOccurences.next() && userNameOccurences.getLong(1) > 0) {
            getUsernameCount.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            throw new UsernameTakenException("Username already exists in database.");
        }
        getUsernameCount.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Sends the customer request to the User service and then processes the reply, or sends a rejection to the
     * requester if the request fails.
     * @param newCustomerRequestJson Json String representing a {@link Customer} that should be created.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doNewCustomerRequest(final String newCustomerRequestJson,
                                      final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding customer creation request.\n", PREFIX);
        usersClient.putFormAsyncWith1Param("/services/users/customer", "customer",
                newCustomerRequestJson,
                (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        handleLoginCreationExceptions(JSONParser.removeEscapeCharacters(newCustomerReplyJson),
                                                      callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Customer creation request failed.");
                    }
                });
    }

    /**
     * Creates login information for the customer in the users database and then sends a callback to the service that
     * sent the customer creation request.
     * @param newCustomerReplyJson Json String representing the customer that should be created in the system.
     * @param callbackBuilder used to send a reply to the service that sent the request.
     */
    private void handleLoginCreationExceptions(final String newCustomerReplyJson,
                                               final CallbackBuilder callbackBuilder) {
        Customer customerToEnroll = jsonConverter.fromJson(newCustomerReplyJson, Customer.class);
        try {
            registerNewCustomerLogin(customerToEnroll);
            sendNewCustomerRequestCallback(newCustomerReplyJson, callbackBuilder);
        } catch (SQLException e) {
            //todo revert customer creation in users database.
            e.printStackTrace();
            callbackBuilder.build().reject("Couldn't create login data.");
        }
    }

    /**
     * Adds login information of a customer to the database.
     * @param customerToEnroll Customer that is to be enrolled into the system.
     * @throws SQLException Thrown if something goes wrong during the enrollment of the customer.
     */
    void registerNewCustomerLogin(final Customer customerToEnroll) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement createCustomerLogin = databaseConnection.getConnection()
                                                                  .prepareStatement(createAuthenticationData);
        createCustomerLogin.setLong(1, customerToEnroll.getCustomerId());       // id
        createCustomerLogin.setString(2, customerToEnroll.getUsername());    // username
        createCustomerLogin.setString(3, customerToEnroll.getPassword());    // password
        createCustomerLogin.executeUpdate();
        createCustomerLogin.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Forwards the created customer back to the service that sent the customer creation request to this service.
     * @param newCustomerReplyJson Json String representing a customer that was created in the system.
     * @param callbackBuilder Json String representing a {@link Customer} that should be created.
     */
    private void sendNewCustomerRequestCallback(final String newCustomerReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Customer creation successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(newCustomerReplyJson);
    }

    /**
     * Checks the login credentials and generates a login token if they're correct.
     * @param callback Used to send a reply to the requesting service.
     * @param authDataJson Json String representing login information
     */
    @RequestMapping(value = "/login", method = RequestMethod.PUT)
    public void login(final Callback<String> callback, final @RequestParam("authData") String authDataJson) {
        Authentication authData = jsonConverter.fromJson(authDataJson, Authentication.class);
        if (authData.getType() == AuthenticationType.LOGIN) {
            try {
                SQLConnection connection = databaseConnectionPool.getConnection();
                PreparedStatement ps = connection.getConnection().prepareStatement(getAuthenticationData1);
                ps.setString(1, authData.getUsername());    // username
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    long userId = rs.getLong("user_id");
                    String password = rs.getString("password");
                    if (password.equals(authData.getPassword())) {
                        // Legitimate info
                        long newToken = secureRandomNumberGenerator.nextLong();
                        setNewToken(userId, newToken);
                        System.out.printf("%s Successfull login for user %s, sending callback.\n", PREFIX,
                                          authData.getUsername());
                        callback.reply(jsonConverter.toJson(JSONParser.createJsonAuthentication(
                                                    encodeCookie(userId, newToken), AuthenticationType.REPLY)));
                    } else {
                        // Illegitimate info
                        callback.reject("Invalid username/password combination");
                    }
                } else {
                    // username not found
                    callback.reject("Username not found");
                }
                rs.close();
                ps.close();
                databaseConnectionPool.returnConnection(connection);
            } catch (SQLException e) {
                callback.reject(e.getMessage());
                e.printStackTrace();
            }
        } else {
            callback.reject("Wrong Data Received");
        }
    }

    /**
     * Used to encode the cookie of a user.
     * @param userID Id of the user.
     * @param token token belonging to this user.
     * @return Cookie with the userId and token of the customer.
     */
    String encodeCookie(final long userID, final long token) {
        return "" + userID + ":" + new String(Base64.getEncoder().encode(("" + token).getBytes()));
    }

    /**
     * Overwrites old token (if any) with a new one and updates the validity.
     * @param id The user_id of the row to update
     * @param token The token to set
     */
    void setNewToken(final long id, final long token) {
        long validity = System.currentTimeMillis() + Variables.TOKEN_VALIDITY * 1000;
        try {
            SQLConnection connection = databaseConnectionPool.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateToken);
            ps.setLong(1, token);       // new token
            ps.setLong(2, validity);    // validity
            ps.setLong(3, id);          // id
            ps.executeUpdate();

            ps.close();
            databaseConnectionPool.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a callback builder for an account link request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkRequestJson Json string representing an {@link AccountLink} that should be created in the
     *                               database.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback,
                                          @RequestParam("request") final String accountLinkRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        AccountLink accountLinkRequest = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        accountLinkRequest.setCustomerId(getCustomerId(cookie));
        System.out.printf("%s Forwarding account link request for customer %d account number %s.\n", PREFIX,
                          accountLinkRequest.getCustomerId(), accountLinkRequest.getAccountNumber());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkExceptions(accountLinkRequest, cookie, callbackBuilder);
    }

    /**
     * Authenticates the account link request and then forwards the request to the Users service.
     * @param accountLinkRequest Account Link that should be executed.
     * @param cookie Cookie of the customer requesting the account link.
     * @param callbackBuilder Used to send the reply back to the requesting service.
     */
    private void handleAccountLinkExceptions(final AccountLink accountLinkRequest, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            accountLinkRequest.setCustomerId(getCustomerIdFromUsername(accountLinkRequest.getUsername()));
            doAccountLinkRequest(jsonConverter.toJson(accountLinkRequest), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("User not authorized, please login.");
        } catch (CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reject("User with username does not exist, please specify correctly.");
        }
    }


    private Long getCustomerIdFromUsername(final String username) throws SQLException, CustomerDoesNotExistException {
        Long customerId;
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getCustomerId = databaseConnection.getConnection()
                                                                    .prepareStatement(getCustomerIdFromUsername);
        getCustomerId.setString(1, username);
        ResultSet customerIdSet = getCustomerId.executeQuery();
        if (customerIdSet.next()) {
            customerId = customerIdSet.getLong("user_id");
        } else {
            throw new CustomerDoesNotExistException("username not found");
        }
        getCustomerId.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return customerId;
    }

    /**
     * Forwards a String representing an account link to the Users database, and processes the reply if it is
     * successfull or sends a rejection to the requesting service if it fails.
     * @param accountLinkRequestJson String representing an {@link AccountLink} that should be executed.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRequest(final String accountLinkRequestJson, final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/account", "body", accountLinkRequestJson,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendAccountLinkRequestCallback(accountLinkReplyJson, callbackBuilder);
                    } else {
                        System.out.println(accountLinkReplyJson);
                        callbackBuilder.build().reject("AccountLink request failed.");
                    }
                }));
    }

    /**
     * Forwards the result of an account link request to the service that sent the request.
     * @param accountLinkReplyJson Json String representing the result of an account link request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendAccountLinkRequestCallback(final String accountLinkReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfull account link, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(accountLinkReplyJson));
    }

    /**
     * Creates a callback builder for the account creation request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account creation request for customer %d.\n", PREFIX, getCustomerId(cookie));
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewAccountExceptions(cookie, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the request to the Users service.
     * @param cookie Cookie of the customer making the request.
     * @param callbackBuilder Used to send the reply back to the requesting service.
     */
    private void handleNewAccountExceptions(final String cookie, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            doNewAccountRequest(getCustomerId(cookie), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("User not authorized, please login.");
        }
    }

    /**
     * Forwards the customerId of the customer a new account should be created for to the Users Service and sends
     * the result back to the requesting service, or rejects the request if the forwarding fails.
     * @param customerId customerId of the customer the account should be created for.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doNewAccountRequest(final Long customerId, final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/account/new", "customerId", customerId,
                (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendNewAccountRequestCallback(newAccountReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("NewAccount request failed.");
                    }
                });
    }

    /**
     * Sends the result of an account creation request to the service that requested it.
     * @param newAccountReplyJson Json String representing a customer with a linked account that was newly created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendNewAccountRequestCallback(final String newAccountReplyJson,
                                               final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account creation request successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newAccountReplyJson));
    }

    /**
     * Creates a callbackbuilder so that the result of the request can be forwarded to the request source and then
     * calls the exception handler to further process the request. removes an account from a customer.
     * @param callback Used to send a reply/rejection to the request source.
     * @param accountNumber AccountNumber that should be removed from the system.
     * @param cookie Cookie of the user that sent the request, should be a user that is linked to the accountNumber.
     */
    @RequestMapping(value = "/account/remove", method = RequestMethod.PUT)
    public void processAccountRemovalRequest(final Callback<String> callback,
                                             @RequestParam("accountNumber") final String accountNumber,
                                             @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Forwarding account removal request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountRemovalExceptions(accountNumber, cookie, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the removal request with the customerId of the user that sent the
     * request to the Users Service. Checking if the accountNumber belongs to the user is done in the Users Service.
     * @param accountNumber AccountNumber that should be removed from the system.
     * @param cookie Cookie of the user that sent the request.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleAccountRemovalExceptions(final String accountNumber, final String cookie,
                                                final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            doAccountRemovalRequest(accountNumber, Long.toString(getCustomerId(cookie)), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("User not authorized, please login.");
        }
    }

    /**
     * Forwards an account removal request to the Users service and sends a callback if the request is successfull, or
     * a rejection if the request fails.
     * @param accountNumber AccountNumber that should be removed from the system.
     * @param customerId CustomerId of the User that sent the request.
     * @param callbackBuilder Used to forward the result of the request to the request source.
     */
    private void doAccountRemovalRequest(final String accountNumber, final String customerId,
                                         final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith2Params("/services/users/account/remove",
                "accountNumber", accountNumber, "customerId", customerId,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendAccountRemovalCallback(replyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("NewAccount request failed.");
                    }
                });
    }

    private void sendAccountRemovalCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account removal successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(replyJson));
    }

    /**
     * Creates a callbackbuilder for the request and then calls the exception handler. Creates a new pincard for a
     * customer.
     * @param callback Used to send the result of the request back to the request source.
     * @param accountNumber AccountNumber the pin card should be linked to.
     * @param cookie Cookie of the user that sent the request, so the system knows who the pincard is for.
     */
    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void processNewPinCardRequest(final Callback<String> callback,
                                         @RequestParam("accountNumber") final String accountNumber,
                                         @RequestParam("cookie") final String cookie) {
        System.out.printf("%s Received new Pin card request, attempting to forward request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewPinCardExceptions(accountNumber, cookie, callbackBuilder);
    }

    /**
     * Tries to authenticate the user that sent the request and then forwards the new pin card request with the
     * customerId of the User that sent the request.
     * @param accountNumber AccountNumber the pin card should be linked to.
     * @param cookie Cookie of the user that sent the request, so the system knows who the pincard is for.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleNewPinCardExceptions(final String accountNumber, final String cookie,
                                            final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            Long customerId = getCustomerId(cookie);
            doNewPinCardRequest(accountNumber, Long.toString(customerId), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("User not authorized, please login.");
        }

    }

    /**
     * Forwards the new pin card request to the Pin service and forwards the result of the request to
     * the service that requested it.
     * @param accountNumber AccountNumber the pin card should be created for.
     * @param customerId The customerId of the user that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void doNewPinCardRequest(final String accountNumber, final String customerId,
                                     final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith2Params("/services/pin/card", "accountNumber", accountNumber,
                "customerId", customerId, (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendNewPinCardCallback(newAccountReplyJson, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("new pin card request failed.");
                    }
                });
    }

    private void sendNewPinCardCallback(final String newPinCardReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New pin card request successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newPinCardReplyJson));
    }

    /**
     * Creates a callbackBuilder for the request so that the result can be sent back to the request source and then
     * calls the exception handler for the request. Removes a pincard belonging to a customer.
     * @param callback Used to send the result of the request back to the request source.
     * @param pinCardJson Json String representing a {@link PinCard} that is to be removed from the system.
     * @param cookie Cookie of the user that sent the request.
     */
    @RequestMapping(value = "/card/remove", method = RequestMethod.PUT)
    public void processPinCardRemovalRequest(final Callback<String> callback,
                                         @RequestParam("pinCard") final String pinCardJson,
                                         @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinCardRemovalExceptions(pinCardJson, cookie, callbackBuilder);
    }

    /**
     * Tries to authenticate the user that sent the request, creates a {@link PinCard} object based on the request
     * json and then forwards the request with the customerId of the user that sent the request.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     * @param cookie Cookie of the user that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void handlePinCardRemovalExceptions(final String pinCardJson, final String cookie,
                                                final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            PinCard pinCard = jsonConverter.fromJson(pinCardJson, PinCard.class);
            pinCard.setCustomerId(getCustomerId(cookie));
            doPinCardRemovalRequest(jsonConverter.toJson(pinCard), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("User not authorized, please login.");
        }
    }

    /**
     * Forwards the pin card removal request to the pin service, forwards the result to the request source if the
     * request is successfull, or sends a rejection to the request source if the request fails.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void doPinCardRemovalRequest(final String pinCardJson, final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith1Param("/services/pin/card/remove",
                "pinCard", pinCardJson, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        sendPinCardRemovalCallback(body, callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Remove pin card request not successfull.");
                    }
                });
    }

    private void sendPinCardRemovalCallback(final String jsonReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Pin card removal successfull, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(jsonReply));
    }


    /**
     * Safely shuts down the AuthenticationService.
     */
    public void shutdown() {
        databaseConnectionPool.close();
    }
}
