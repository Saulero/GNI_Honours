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
 * @author Saul/Noel
 * @version 2
 */
@RequestMapping("/authentication")
class AuthenticationService {
    /** Connection to the users service. */
    private HttpClient usersClient;
    /** Database connection pool containing persistent database connections. */
    private ConnectionPool databaseConnectionPool;
    /** Secure Random Number Generator. */
    private SecureRandom secureRandomNumberGenerator;
    /** Used for Json conversions. */
    private Gson jsonConverter;

    /**
     * Constructor.
     */
    AuthenticationService(final int usersPort, final String usersHost) {
        this.usersClient = httpClientBuilder().setHost(usersHost).setPort(usersPort).buildAndStart();
        this.databaseConnectionPool = new ConnectionPool();
        this.secureRandomNumberGenerator = new SecureRandom();
        this.jsonConverter = new Gson();
    }

    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback,
                                   @RequestParam("request") final String dataRequestJson,
                                   @RequestParam("cookie") final String cookie) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleDataRequestExceptions(dataRequestJson, cookie, callbackBuilder);
    }

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

    public void authenticateRequest(final String cookie) throws UserNotAuthorizedException, SQLException {
        Long[] cookieData = decodeCookie(cookie);
        long customerId = cookieData[0];
        long cookieToken = cookieData[1];
        System.out.println(customerId);
        System.out.println(cookieToken);
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
                throw new UserNotAuthorizedException("Token not legitimate or expired.");
            }
        } else {
            throw new UserNotAuthorizedException("UserId not found.");
        }
        authenticationData.close();
        getAuthenticationData.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    private Long[] decodeCookie(final String cookie) {
        String[] cookieParts = cookie.split(":");
        Long[] cookieData = new Long[2];
        cookieData[0] = Long.parseLong(cookieParts[0]); //customerId
        System.out.println("parsed customerId");
        cookieData[1] = Long.parseLong(new String(Base64.getDecoder().decode(cookieParts[1].getBytes()))); //customerToken
        System.out.println("returning");
        return cookieData;
    }

    /**
     * Updates the validity of the token upon reuse.
     * @param id The user_id of the row to update
     */
    private void updateTokenValidity(final long id) {
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

    private Long getCustomerId(final String cookie) {
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
        System.out.println("UI: Sending data request to Authentication Service.");
        usersClient.getAsyncWith1Param("/services/users/data", "request",
                                        dataRequestJson, (httpStatusCode, httpContentType, dataReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                sendDataRequestCallback(dataReplyJson, callbackBuilder);
            } else {
                callbackBuilder.build().reject("Transaction history request failed.");
            }
        });
    }

    private void sendDataRequestCallback(final String dataReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println("Auth: Sending data reply back to UI.");
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(dataReplyJson));
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

    private void handleTransactionRequestExceptions(final String transactionRequestJson, final String cookie,
                                                    final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            //todo check if customer is allowed to send money from this sourceAccount.
            doTransactionRequest(transactionRequestJson, callbackBuilder);
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
    private void doTransactionRequest(final String transactionRequestJson, final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Forwarding transaction request.");
        usersClient.putFormAsyncWith1Param("/services/users/transaction", "body",
                transactionRequestJson,
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
        System.out.println("UI: Transaction successfully executed, sent callback.");
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(transactionReplyJson));
    }

    // TODO Should be invoked when a new user is created
    /**
     * Creates new login credentials for a customer.
     * @param callback Used to send a reply back to the UserService
     * @param newCustomerRequestJson Json String representing login information
     */
    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomerRequest(final Callback<String> callback,
                                          @RequestParam("customer") final String newCustomerRequestJson) {
        System.out.println(newCustomerRequestJson);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleUsernameValidationExceptions(newCustomerRequestJson, callbackBuilder);
    }

    private void handleUsernameValidationExceptions(final String newCustomerRequestJson,
                                                    final CallbackBuilder callbackBuilder) {
        try {
            validateUsername(jsonConverter.fromJson(newCustomerRequestJson, Customer.class));
            doNewCustomerRequest(newCustomerRequestJson, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication databse.");
        } catch (UsernameTakenException e) {
            callbackBuilder.build().reject("Username taken, please choose a different username.");
        }
    }

    private void validateUsername(final Customer customerToEnroll) throws SQLException, UsernameTakenException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getUsernameCount = databaseConnection.getConnection()
                .prepareStatement(getLoginUsernameCount);
        getUsernameCount.setString(1, customerToEnroll.getUsername());
        ResultSet userNameOccurences = getUsernameCount.executeQuery();
        if (userNameOccurences.next() && userNameOccurences.getLong(1) > 0) {
            throw new UsernameTakenException("Username already exists in database.");
        }
        getUsernameCount.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Sends the customer request to the User service and then processes the reply, or sends a rejection to the
     * requester if the request fails..
     * @param newCustomerRequestJson Json String representing a Customer that should be created {@link Customer}.
     * @param callbackBuilder Used to send the response of the creation request back to the source of the request.
     */
    private void doNewCustomerRequest(final String newCustomerRequestJson,
                                      final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Sending customer creation request to Users");
        usersClient.putFormAsyncWith1Param("/services/users/customer", "customer",
                newCustomerRequestJson,
                (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        handleLoginCreationExceptions(JSONParser.removeEscapeCharacters(newCustomerReplyJson), callbackBuilder);
                    } else {
                        callbackBuilder.build().reject("Customer creation request failed.");
                    }
                });
    }

    private void handleLoginCreationExceptions(final String newCustomerReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.println(newCustomerReplyJson);
        Customer customerToEnroll = jsonConverter.fromJson(newCustomerReplyJson, Customer.class);
        try {
            registerNewCustomerLogin(customerToEnroll);
            sendNewCustomerRequestCallback(newCustomerReplyJson, callbackBuilder);
        } catch (SQLException e) {
            //todo revert customer creation in users database.
            callbackBuilder.build().reject("Couldn't create login data.");
        }
    }

    private void registerNewCustomerLogin(final Customer customerToEnroll) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement createCustomerLogin = databaseConnection.getConnection()
                                                                  .prepareStatement(createAuthenticationData);
        createCustomerLogin.setLong(1, customerToEnroll.getId());       // id
        createCustomerLogin.setString(2, customerToEnroll.getUsername());    // username
        createCustomerLogin.setString(3, customerToEnroll.getPassword());    // password
        createCustomerLogin.executeUpdate();
        createCustomerLogin.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Forwards the created customer back to the service that sent the customer creation request to this service.
     * @param newCustomerReplyJson Json String representing a customer that was created in the system.
     * @param callbackBuilder Json String representing a Customer that should be created {@link Customer}.
     */
    private void sendNewCustomerRequestCallback(final String newCustomerReplyJson,
                                                final CallbackBuilder callbackBuilder) {
        System.out.println("UI: Customer creation successfull, sending callback.");
        callbackBuilder.build().reply(newCustomerReplyJson);
    }

    // TODO Should be invoked when login credentials are entered
    /**
     * Checks the login credentials and generates a login token if they're correct.
     * @param callback Used to send a reply to the requesting service.
     * @param authDataJson Json String representing login information
     */
    @RequestMapping(value = "/login", method = RequestMethod.PUT)
    public void login(final Callback<String> callback, final @RequestParam("authData") String authDataJson) {
        System.out.println(authDataJson);
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
                        System.out.println(jsonConverter.toJson(JSONParser.createJsonAuthentication(
                                encodeCookie(userId, newToken), AuthenticationType.REPLY)));
                        callback.resolve(jsonConverter.toJson(JSONParser.createJsonAuthentication(
                                encodeCookie(userId, newToken), AuthenticationType.REPLY)));
                        //callback.reply(jsonConverter.toJson(JSONParser.createJsonAuthentication(
                        //                            encodeCookie(userId, newToken), AuthenticationType.REPLY)));
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

    private String encodeCookie(final long userID, final long token) {
        return "" + userID + ":" + new String(Base64.getEncoder().encode(("" + token).getBytes()));
    }

    /**
     * Overwrites old token (if any) with a new one and updates the validity.
     * @param id The user_id of the row to update
     * @param token The token to set
     */
    private void setNewToken(final long id, final long token) {
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
     * Creates a callback builder for the account link request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param accountLinkRequestJson Json string representing an AccountLink that should be created in the
     *                               database {@link AccountLink}.
     */
    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback,
                                          @RequestParam("request") final String accountLinkRequestJson,
                                          @RequestParam("cookie") final String cookie) {
        AccountLink accountLinkRequest = jsonConverter.fromJson(accountLinkRequestJson, AccountLink.class);
        System.out.printf("UI: Received account link request for customer %d account number %s\n",
                accountLinkRequest.getCustomerId(), accountLinkRequest.getAccountNumber());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkExceptions(accountLinkRequest, cookie, callbackBuilder);
    }

    private void handleAccountLinkExceptions(final AccountLink accountLinkRequest, final String cookie,
                                             final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            accountLinkRequest.setCustomerId(getCustomerId(cookie));
            doAccountLinkRequest(jsonConverter.toJson(accountLinkRequest), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("User not authorized, please login.");
        }
    }

    /**
     * Forwards a String representing an account link to the Users database, and processes the reply if it is successfull
     * or sends a rejection to the requesting service if it fails.
     * @param accountLinkRequestJson String representing an account link that should be executed {@link AccountLink}.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRequest(final String accountLinkRequestJson, final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/account", "body", accountLinkRequestJson,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        sendAccountLinkRequestCallback(accountLinkReplyJson, callbackBuilder);
                    } else {
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
        System.out.println("Auth: Successfull account link, sending callback.");
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(accountLinkReplyJson));
    }

    /**
     * Creates a callback builder for the account creation request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     */
    //todo refactor so that only customerId from cookie is needed.
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("request") final String newAccountRequestJson,
                                         @RequestParam("cookie") final String cookie) {
        Customer accountOwner = jsonConverter.fromJson(newAccountRequestJson, Customer.class);
        System.out.printf("UI: Received account creation request for customer %d\n", accountOwner.getId());
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder()
                .withStringCallback(callback);
        doNewAccountRequest(newAccountRequestJson, callbackBuilder);
    }

    private void handleNewAccountExceptions(final String newAccountRequestJson, final String cookie,
                                            final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie);
            doNewAccountRequest(newAccountRequestJson, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reject("Error connecting to authentication database.");
        } catch (UserNotAuthorizedException e) {
            callbackBuilder.build().reject("User not authorized, please login.");
        }
    }

    /**
     * Forwards the Json String representing a customer with the account to be created to the Users Service and sends
     * the result back to the requesting service, or rejects the request if the forwarding fails.
     * @param newAccountRequestJson Json String representing a customer object which is the account owner, with an
     *                              Account object inside representing the account that should be created.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doNewAccountRequest(final String newAccountRequestJson,
                                     final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/account/new", "body", newAccountRequestJson,
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
        System.out.println("UI: Successfull account creation request, sending callback.");
        callbackBuilder.build().reply(JSONParser.removeEscapeCharacters(newAccountReplyJson));
    }



    /**
     * Safely shuts down the AuthenticationService.
     */
    public void shutdown() {
        databaseConnectionPool.close();
    }
}
