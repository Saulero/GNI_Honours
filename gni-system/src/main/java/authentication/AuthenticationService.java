package authentication;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
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
import users.CustomerDoesNotExistException;
import util.JSONParser;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;

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
    /** Connection to the ledger service. */
    private HttpClient ledgerClient;
    /** Connection to the SystemInformation service. */
    private HttpClient systemInformationClient;
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
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    AuthenticationService(final int servicePort, final String serviceHost,
                          final int sysInfoPort, final String sysInfoHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
        this.databaseConnectionPool = new ConnectionPool();
        this.secureRandomNumberGenerator = new SecureRandom();
        this.jsonConverter = new Gson();
        sendServiceInformation(servicePort, serviceHost);
    }

    /**
     * Method that sends the service information of this service to the SystemInformationService.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     */
    private void sendServiceInformation(final int servicePort, final String serviceHost) {
        ServiceInformation serviceInfo = new ServiceInformation(
                servicePort, serviceHost, ServiceType.AUTHENTICATION_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", jsonConverter.toJson(serviceInfo), (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode != HTTP_OK) {
                System.err.println("Problem with connection to the SystemInformationService.");
                System.err.println("Shutting down the Authentication service.");
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
        ServiceInformation users = sysInfo.getUsersServiceInformation();
        ServiceInformation pin = sysInfo.getPinServiceInformation();
        ServiceInformation ledger = sysInfo.getLedgerServiceInformation();

        this.usersClient = httpClientBuilder().setHost(users.getServiceHost())
                .setPort(users.getServicePort()).buildAndStart();
        this.pinClient = httpClientBuilder().setHost(pin.getServiceHost())
                .setPort(pin.getServicePort()).buildAndStart();
        this.ledgerClient = httpClientBuilder().setHost(ledger.getServiceHost())
                .setPort(ledger.getServicePort()).buildAndStart();

        System.out.printf("%s Initialization of Authentication service connections complete.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
    }

    private boolean isAdmin(final MethodType methodType, final String cookie) throws SQLException {
        SQLConnection connection = databaseConnectionPool.getConnection();
        PreparedStatement ps = connection.getConnection().prepareStatement(SQLStatements.getAdminPermissions);
        ps.setLong(1, getCustomerId(cookie));
        ResultSet rs = ps.executeQuery();
        boolean res = false;

        while (rs.next()) {
            if (rs.getLong("permission_id") == methodType.getId()) {
                res = true;
                break;
            }
        }

        rs.close();
        ps.close();
        databaseConnectionPool.returnConnection(connection);
        return res;
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
            MessageWrapper messageWrapper = jsonConverter.fromJson(
                    JSONParser.removeEscapeCharacters(dataRequestJson), MessageWrapper.class);

            authenticateRequest(cookie, messageWrapper.getMethodType());
            messageWrapper.setAdmin(isAdmin(messageWrapper.getMethodType(), cookie));
            DataRequest dataRequest = ((DataRequest) messageWrapper.getData());
            dataRequest.setCustomerId(getCustomerId(cookie));
            messageWrapper.setData(dataRequest);
            doDataRequest(messageWrapper, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    /**
     * Checks if a request is authorized by checking if the token in the cookie is the correct token for that customer
     * and the token is still valid if one of these conditions is not met a UserNotAuthorizedException is thrown.
     * @param cookie Cookie of a customer
     * @param methodType Type of method this request is for
     * @throws UserNotAuthorizedException thrown when the token is not legitimate/expired or the userId does not exist.
     * @throws SQLException thrown when there is a problem fetching the authentication data from the database.
     * @throws AccountFrozenException thrown when the account is frozen and the requested method is not allowed.
     */
    void authenticateRequest(final String cookie, final MethodType methodType)
            throws UserNotAuthorizedException, SQLException, AccountFrozenException {
        Long[] cookieData = decodeCookie(cookie);
        long customerId = cookieData[0];
        long cookieToken = cookieData[1];
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getAuthenticationData = databaseConnection.getConnection()
                                                                    .prepareStatement(getAuthenticationData2);
        getAuthenticationData.setLong(1, customerId);
        ResultSet authenticationData = getAuthenticationData.executeQuery();
        if (authenticationData.next()) {
            if (authenticationData.getBoolean("frozen") && !methodType.isAllowedWhenFrozen()) {
                throw new AccountFrozenException(
                        "User has no authorization to do this as long as the account is frozen.");
            }
            if (authenticationData.getBoolean("child") && !methodType.isAllowedWhenChild()) {
                throw new UserNotAuthorizedException(
                        "User has no authorization to do this action with a children's account.");
            }

            long customerToken = authenticationData.getLong("token");
            long tokenValidity = authenticationData.getLong("token_validity");
            if (cookieToken == customerToken && System.currentTimeMillis() < tokenValidity) {
                updateTokenValidity(customerId);
            } else {
                authenticationData.close();
                getAuthenticationData.close();
                databaseConnectionPool.returnConnection(databaseConnection);
                throw new UserNotAuthorizedException("Login Token not legitimate or expired.");
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

    private void checkFrozenAccount(final MethodType methodType) {

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
     * @param dataRequest A dataRequest that should be sent to the UsersService.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doDataRequest(final MessageWrapper dataRequest, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding data request.\n", PREFIX);
        if (dataRequest.getMethodType() == MethodType.GET_OVERDRAFT_LIMIT) {
            ledgerClient.putFormAsyncWith1Param("/services/ledger/overdraft/get",
                    "data", jsonConverter.toJson(dataRequest),
                    (httpStatusCode, httpContentType, replyJson) -> {
                        if (httpStatusCode == HTTP_OK) {
                            MessageWrapper messageWrapper = jsonConverter.fromJson(
                                    JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                            if (!messageWrapper.isError()) {
                                sendGetOverdraftLimitCallback(replyJson, callbackBuilder);
                            } else {
                                callbackBuilder.build().reply(replyJson);
                            }
                        } else {
                            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                    true, 500,
                                    "An unknown error occurred.",
                                    "There was a problem with one of the HTTP requests")));
                        }
                    });
        } else {
            usersClient.getAsyncWith1Param("/services/users/data", "data",
                    jsonConverter.toJson(dataRequest), (httpStatusCode, httpContentType, dataReplyJson) -> {
                        if (httpStatusCode == HTTP_OK) {
                            MessageWrapper messageWrapper = jsonConverter.fromJson(
                                    JSONParser.removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                            if (!messageWrapper.isError()) {
                                handleDataReply((DataReply) messageWrapper.getData(), callbackBuilder);
                            } else {
                                callbackBuilder.build().reply(dataReplyJson);
                            }
                        } else {
                            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                    true, 500, "An unknown error occurred.",
                                    "There was a problem with one of the HTTP requests")));
                        }
                    });
        }
    }

    /**
     * Send a callback to the original source.
     * @param replyJson JSON String representing the reply
     * @param callbackBuilder Used to forward the result of the request to the request source.
     */
    private void sendGetOverdraftLimitCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Get overdraft limit request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    private void handleDataReply(final DataReply dataReply, final CallbackBuilder callbackBuilder) {
        try {
            if (dataReply.getType() == RequestType.CUSTOMERACCESSLIST || dataReply.getType() == RequestType.ACCOUNTACCESSLIST) {
                for (AccountLink link : dataReply.getAccounts()) {
                    link.setUsername(getUserNameFromCustomerId(link.getCustomerId()));
                }
            }
            sendDataRequestCallback(dataReply, callbackBuilder);
        } catch (CustomerDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", "The provided customer does not seem to exist.")));
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        }
    }

    private String getUserNameFromCustomerId(final Long customerId) throws SQLException, CustomerDoesNotExistException {
        String username;
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
    }

    /**
     * Sends the result of a data request back to the service that requested it.
     * @param dataReply The reply that was received.
     * @param callbackBuilder Used to send back the reply to the service that requested it.
     */
    private void sendDataRequestCallback(final DataReply dataReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Data request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", dataReply)));
    }

    /**
     * Creates a callback builder to forward the result of the request to the requester, and then forwards the request
     * to the Users service.
     * @param callback Used to send the reply of User service to the source of the request.
     * @param data A Transaction object that is to be processed {@link Transaction}.
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("data") final String data) {
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        handleTransactionRequestExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Checks if the request is authorized and then forwards the transaction to the TransactionDispatch service.
     * @param messageWrapper MessageWrapper containing a transaction request & cookie
     * @param callbackBuilder Used to send the reply back to the service that sent the request.
     */
    private void handleTransactionRequestExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            doTransactionRequest(messageWrapper, getCustomerId(messageWrapper.getCookie()), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    /**
     * Forwards transaction request to the User service and forwards the reply or sends a rejection if the request
     * fails.
     * @param messageWrapper MessageWrapper containing a transaction request that should be processed.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doTransactionRequest(final MessageWrapper messageWrapper, final Long customerId,
                                      final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding transaction request.\n", PREFIX);
        usersClient.putFormAsyncWith2Params("/services/users/transaction", "request",
                jsonConverter.toJson(messageWrapper), "customerId", customerId.toString(),
                (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper responseWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!responseWrapper.isError()) {
                            sendTransactionRequestCallback(transactionReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(transactionReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
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
        System.out.printf("%s Transaction successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(transactionReplyJson);
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
            Customer customer = jsonConverter.fromJson(newCustomerRequestJson, Customer.class);
            validateUsername(customer);
            if (customer.isChild()) {
                String[] guardians = customer.getGuardians();
                Long[] res = new Long[guardians.length];
                for (int i = 0; i < guardians.length; i++) {
                    res[i] = getCustomerIdFromUsername(guardians[i]);
                }
                customer.setGuardianIds(res);
            }
            doNewCustomerRequest(newCustomerRequestJson, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to authentication database.")));
        } catch (UsernameTakenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 418, "One of the parameters has an invalid value.",
                    "Username taken, please choose a different username.")));
        } catch (CustomerDoesNotExistException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 418, "One of the parameters has an invalid value.",
                    "One of the guardians does not exist.")));
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
        ResultSet userNameOccurrences = getUsernameCount.executeQuery();
        if (userNameOccurrences.next() && userNameOccurrences.getLong(1) > 0) {
            userNameOccurrences.close();
            getUsernameCount.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            throw new UsernameTakenException("Username already exists in database.");
        }
        userNameOccurrences.close();
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
        usersClient.putFormAsyncWith1Param("/services/users/customer", "customer", newCustomerRequestJson,
                (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(newCustomerReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            handleLoginCreationExceptions((Customer) messageWrapper.getData(), callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(newCustomerReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Creates login information for the customer in the users database and then sends a callback to the service that
     * sent the customer creation request.
     * @param newCustomer The customer that should be created in the system.
     * @param callbackBuilder used to send a reply to the service that sent the request.
     */
    private void handleLoginCreationExceptions(final Customer newCustomer,
                                               final CallbackBuilder callbackBuilder) {
        try {
            registerNewCustomerLogin(newCustomer);
            sendNewCustomerRequestCallback(newCustomer, callbackBuilder);
        } catch (SQLException e) {
            //todo revert customer creation in users database.
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to authentication database.")));
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
        createCustomerLogin.setBoolean(4, customerToEnroll.isChild());
        createCustomerLogin.executeUpdate();
        createCustomerLogin.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Forwards the created customer back to the service that sent the customer creation request to this service.
     * @param newCustomer A customer that was created in the system.
     * @param callbackBuilder Json String representing a {@link Customer} that should be created.
     */
    private void sendNewCustomerRequestCallback(final Customer newCustomer, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Customer creation successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", newCustomer)));
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
                        System.out.printf("%s Successful login for user %s, sending callback.\n", PREFIX,
                                          authData.getUsername());
                        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                false, 200, "Normal Reply",
                                new Authentication(encodeCookie(userId, newToken), AuthenticationType.REPLY))));
                    } else {
                        // Illegitimate info
                        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 422,
                                "The user could not be authenticated, a wrong combination of credentials was provided.")));
                    }
                } else {
                    // username not found
                    callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                            "One of the parameters has an invalid value.",
                            "The username does not seem to exist.")));
                }
                rs.close();
                ps.close();
                databaseConnectionPool.returnConnection(connection);
            } catch (SQLException e) {
                e.printStackTrace();
                callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                        "Error connecting to the authentication database.")));
            }
        } else {
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Unknown error occurred.")));
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
     * @param data Messagewrapper containing an {@link AccountLink} & cookie.
     */
    @RequestMapping(value = "/accountLink", method = RequestMethod.PUT)
    public void processAccountLinkRequest(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Forwarding account link request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the account link request and then forwards the request to the Users service.
     * @param messageWrapper MessageWrapper containing Account Link that should be executed & cookie.
     * @param callbackBuilder Used to send the reply back to the requesting service.
     */
    private void handleAccountLinkExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            AccountLink accountLinkRequest = (AccountLink) messageWrapper.getData();
            accountLinkRequest.setCustomerId(getCustomerIdFromUsername(accountLinkRequest.getUsername()));
            doAccountLinkRequest(jsonConverter.toJson(accountLinkRequest),
                    getCustomerId(messageWrapper.getCookie()), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to authentication database.")));
        } catch (CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 418, "One of the parameters has an invalid value.",
                    "User with username does not appear to exist.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.", e.getMessage())));
        }
    }


    private Long getCustomerIdFromUsername(final String username) throws SQLException, CustomerDoesNotExistException {
        Long customerId;
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement ps = databaseConnection.getConnection().prepareStatement(getCustomerIdFromUsername);
        ps.setString(1, username);
        ResultSet customerIdSet = ps.executeQuery();
        if (customerIdSet.next()) {
            customerId = customerIdSet.getLong("user_id");
        } else {
            throw new CustomerDoesNotExistException("username not found");
        }
        ps.close();
        databaseConnectionPool.returnConnection(databaseConnection);
        return customerId;
    }

    /**
     * Forwards a String representing an account link to the Users database, and processes the reply if it is
     * successful or sends a rejection to the requesting service if it fails.
     * @param accountLinkRequestJson String representing an {@link AccountLink} that should be executed.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doAccountLinkRequest(final String accountLinkRequestJson, final long requesterId,
                                      final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith2Params("/services/users/accountLink", "body",
                accountLinkRequestJson, "requesterId", requesterId,
                ((httpStatusCode, httpContentType, accountLinkReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(accountLinkReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendAccountLinkRequestCallback(accountLinkReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(accountLinkReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                }));
    }

    /**
     * Forwards the result of an account link request to the service that sent the request.
     * @param accountLinkReplyJson Json String representing the result of an account link request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendAccountLinkRequestCallback(
            final String accountLinkReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successful account link, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(accountLinkReplyJson);
    }

    @RequestMapping(value = "/accountLink/remove", method = RequestMethod.PUT)
    public void processAccountLinkRemoval(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Forwarding account link removal.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountLinkRemovalExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the account link removal request and then forwards the request to the Users service.
     * @param messageWrapper MessageWrapper with Account Link that should be removed & cookie.
     * @param callbackBuilder Used to send the reply back to the requesting service.
     */
    private void handleAccountLinkRemovalExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            AccountLink accountLink = (AccountLink) messageWrapper.getData();
            accountLink.setCustomerId(getCustomerIdFromUsername(accountLink.getUsername()));
            doAccountLinkRemoval(accountLink, "" + getCustomerId(messageWrapper.getCookie()), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", "User with username does not appear to exist.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void doAccountLinkRemoval(final AccountLink accountLink, final String requesterId,
                                      final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith2Params("/services/users/accountLink/remove", "request",
                                            jsonConverter.toJson(accountLink), "requesterId", requesterId,
                                            (httpStatusCode, httpContentType, removalReplyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(
                        JSONParser.removeEscapeCharacters(removalReplyJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    System.out.printf("%s Forwarding accountLink removal reply.\n", PREFIX);
                }
                callbackBuilder.build().reply(removalReplyJson);
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                        "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Creates a callback builder for the account creation request and then forwards the request to the UsersService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param data MessageWrapper containing cookie etc.
     */
    @RequestMapping(value = "/account/new", method = RequestMethod.PUT)
    public void processNewAccountRequest(final Callback<String> callback,
                                         @RequestParam("data") final String data) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        System.out.printf("%s Forwarding account creation request for customer %d.\n",
                PREFIX, getCustomerId(messageWrapper.getCookie()));
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewAccountExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the request to the Users service.
     * @param messageWrapper Cookie of the customer making the request.
     * @param callbackBuilder Used to send the reply back to the requesting service.
     */
    private void handleNewAccountExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            doNewAccountRequest(getCustomerId(messageWrapper.getCookie()), callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(newAccountReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewAccountRequestCallback(newAccountReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(newAccountReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
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
        System.out.printf("%s Account creation request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(newAccountReplyJson);
    }

    /**
     * Creates a callbackbuilder so that the result of the request can be forwarded to the request source and then
     * calls the exception handler to further process the request. removes an account from a customer.
     * @param callback Used to send a reply/rejection to the request source.
     * @param data MessageWrapper containing AccountNumber that should be removed from the system & cookie etc.
     */
    @RequestMapping(value = "/account/remove", method = RequestMethod.PUT)
    public void processAccountRemovalRequest(final Callback<String> callback,
                                             @RequestParam("data") final String data) {
        System.out.printf("%s Forwarding account removal request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleAccountRemovalExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the removal request with the customerId of the user that sent the
     * request to the Users Service. Checking if the accountNumber belongs to the user is done in the Users Service.
     * @param messageWrapper MessageWrapper containing AccountNumber that should be removed from the system & cookie.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleAccountRemovalExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        String cookie = messageWrapper.getCookie();
        String accountNumber = (String) messageWrapper.getData();

        try {
            authenticateRequest(cookie, messageWrapper.getMethodType());
            if (accountNumber.endsWith("S")) {
                doCloseSavingsAccountRequest(accountNumber.substring(0, accountNumber.length() - 1), callbackBuilder);
            } else if (accountNumber.endsWith("C")) {
                doCloseCreditCardRequest(accountNumber.substring(0, accountNumber.length() - 1),
                                        getCustomerId(cookie), callbackBuilder);
            } else {
                doAccountRemovalRequest(accountNumber, Long.toString(getCustomerId(cookie)), callbackBuilder);
            }
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to the authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    /**
     * Forwards an account removal request to the Users service and sends a callback if the request is successful, or
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
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            CloseAccountReply reply = (CloseAccountReply) messageWrapper.getData();
                            if (!reply.isSuccessful()) {
                                callbackBuilder.build().reply(replyJson);
                            } else {
                                if (reply.isCustomerRemoved()) {
                                    removeCustomerTokens(customerId);
                                }
                                doAccountPinCardsRemovalRequest(accountNumber, callbackBuilder);
                            }
                        } else {
                            callbackBuilder.build().reply(replyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void removeCustomerTokens(final String customerId) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement removeTokens = databaseConnection.getConnection()
                    .prepareStatement(SQLStatements.removeCustomerTokens);
            removeTokens.setLong(1, Long.parseLong(customerId));
            removeTokens.execute();
            removeTokens.close();
            databaseConnectionPool.returnConnection(databaseConnection);
        } catch (SQLException e) {
            System.out.printf("%s failed to remove tokens when deleting customer.", PREFIX);
        }
    }

    private void doCloseCreditCardRequest(final String accountNumber, final Long customerId,
                                          final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith2Params("/services/pin/creditCard/remove", "accountNumber",
                accountNumber, "customerId", customerId,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendCloseCreditCardCallback(replyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(replyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendCloseCreditCardCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Credit card removal successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    /**
     * Sends a request to remove all pincards for the account with accountNumber to the pinService.
     * @param accountNumber AccountNumber of the account for which all pinCards need to be removed.
     * @param callbackBuilder Used to forward the result of the request to the request source.
     */
    private void doAccountPinCardsRemovalRequest(final String accountNumber, final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith1Param("/services/pin/account/remove", "accountNumber",
                                        accountNumber, (httpStatusCode, httpContentType, replyJson) -> {
            if (httpStatusCode == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(
                        JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    sendAccountRemovalCallback(replyJson, callbackBuilder);
                } else {
                    callbackBuilder.build().reply(replyJson);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                        true, 500, "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests")));
            }
        });
    }

    private void sendAccountRemovalCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Account removal successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    /**
     * Creates a callbackbuilder for the request and then calls the exception handler. Creates a new pincard for a
     * customer.
     * @param callback Used to send the result of the request back to the request source.
     * @param data MessageWrapper containing all the required data.
     */
    @RequestMapping(value = "/card", method = RequestMethod.PUT)
    public void processNewPinCardRequest(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Received new Pin card request, attempting to forward request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewPinCardExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Tries to authenticate the user that sent the request and then forwards the new pin card request with the
     * customerId of the User that sent the request.
     * @param messageWrapper MessageWrapper containing all the required data.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleNewPinCardExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        AccountLink data = (AccountLink) messageWrapper.getData();
        String cookie = messageWrapper.getCookie();
        String username = data.getUsername();
        String accountNumber = data.getAccountNumber();

        try {
            authenticateRequest(cookie, messageWrapper.getMethodType());
            Long requesterId = getCustomerId(cookie);
            Long ownerId;
            if (username == null) {
                ownerId = requesterId;
            } else {
                ownerId = getIdFromUsername(username);
            }
            if (ownerId != null) {
                doNewPinCardRequest(accountNumber, Long.toString(requesterId), Long.toString(ownerId), callbackBuilder);
            } else {
                System.out.printf("%s Rejecting, OwnerId could not be found. Username does not exist.\n", PREFIX);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                        true, 418, "One of the parameters has an invalid value.",
                        "The username does not seem to exist.")));
            }
        } catch (SQLException e) {
            System.out.printf("%s Rejecting, Error connecting to authentication database.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.", e.getMessage())));
        }

    }

    /**
     * Fetch the customerId of the user with username.
     * @param username Username of the user the customerId must be located for.
     */
    private Long getIdFromUsername(final String username) {
        try {
            SQLConnection databaseConnection = databaseConnectionPool.getConnection();
            PreparedStatement getCustomerID = databaseConnection.getConnection()
                    .prepareStatement(SQLStatements.getCustomerIdFromUsername);
            getCustomerID.setString(1, username);
            ResultSet customerIds = getCustomerID.executeQuery();
            Long customerId = null;
            if (customerIds.next()) {
                customerId = customerIds.getLong("user_id");
            }
            getCustomerID.close();
            databaseConnectionPool.returnConnection(databaseConnection);
            return customerId;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Forwards the new pin card request to the Pin service and forwards the result of the request to
     * the service that requested it.
     * @param accountNumber AccountNumber the pin card should be created for.
     * @param requesterId CustomerId of the user that sent the request.
     * @param ownerId The customerId of the user that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void doNewPinCardRequest(final String accountNumber, final String requesterId, final String ownerId,
                                     final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith3Params("/services/pin/card", "requesterId", requesterId,
                "ownerId", ownerId, "accountNumber", accountNumber,
                (httpStatusCode, httpContentType, newAccountReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(newAccountReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendNewPinCardCallback(newAccountReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(newAccountReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendNewPinCardCallback(final String newPinCardReplyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New pin card request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(newPinCardReplyJson);
    }

    /**
     * Creates a callback builder for a pinCard unblock request and then forwards the request to the PinService.
     * @param callback Used to send the result of the request back to the source of the request.
     * @param data Json string representing a {@link PinCard} that should be unblocked.
     */
    @RequestMapping(value = "/unblockCard", method = RequestMethod.PUT)
    public void processPinCardUnblockRequest(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Forwarding pinCard unblock request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handlePinCardUnblockExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the PinCard unblock request and then forwards the request to the Pinservice.
     * @param messageWrapper PinCard that should be unblocked & cookie.
     * @param callbackBuilder Used to send the reply back to the requesting service.
     */
    private void handlePinCardUnblockExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            doPinCardUnblockRequest(jsonConverter.toJson(messageWrapper.getData()), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 500, "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 419, "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    /**
     * Forwards a String representing a pinCard to the PinService, and processes the reply if it is
     * successful or sends a rejection to the requesting service if it fails.
     * @param requestJson String representing an {@link PinCard} that should be unblocked.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void doPinCardUnblockRequest(final String requestJson, final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith1Param("/services/pin/unblockCard", "pinCard",
                requestJson, ((httpStatusCode, httpContentType, body) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendPinCardUnblockCallback(body, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                }));
    }

    /**
     * Forwards the result of a pinCard unblock request to the service that sent the request.
     * @param replyJson Json String representing the result of a PinCard unblock request.
     * @param callbackBuilder Used to send the result of the request back to the source of the request.
     */
    private void sendPinCardUnblockCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successful pinCard unblock, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    /**
     * Creates a callbackBuilder so that the result of the request can be forwarded to the request source and then
     * calls the exception handler to further process the request. Sets a new overdraft limit for an account.
     * @param callback Used to send a reply/rejection to the request source.
     * @param data MessageWrapper containing all necessary data.
     */
    @RequestMapping(value = "/overdraft/set", method = RequestMethod.PUT)
    public void processSetOverdraftLimit(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Processing SetOverdraftLimit request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleSetOverdraftLimitExceptions(messageWrapper, callbackBuilder);
    }

    /**
     * Authenticates the request and then forwards the request with the accountNumber to ledger.
     * @param messageWrapper MessageWrapper containing all necessary data.
     * @param callbackBuilder Used to send the result of the request to the request source.
     */
    private void handleSetOverdraftLimitExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            doSetOverdraftLimitRequest(((Transaction) messageWrapper.getData()).getSourceAccountNumber(),
                    ((Transaction) messageWrapper.getData()).getNewBalance(), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    /**
     * Forwards a setOverdraftLimit request to Ledger service and sends a callback if the request is successful, or
     * an error if the request fails.
     * @param accountNumber AccountNumber of which the limit should be set.
     * @param overdraftLimit New overdraft limit
     * @param callbackBuilder Used to forward the result of the request to the request source.
     */
    private void doSetOverdraftLimitRequest(final String accountNumber, final Double overdraftLimit,
                                         final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith2Params("/services/ledger/overdraft/set",
                "accountNumber", accountNumber, "overdraftLimit", overdraftLimit,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendSetOverdraftLimitCallback(replyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(replyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
        }

    /**
     * Send a callback to the original source.
     * @param replyJson JSON String representing the reply
     * @param callbackBuilder Used to forward the result of the request to the request source.
     */
    private void sendSetOverdraftLimitCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New overdraft limit set successfully, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    @RequestMapping(value = "/savingsAccount", method = RequestMethod.PUT)
    public void openSavingsAccount(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Received open savings account request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleOpenSavingsAccountExceptions(messageWrapper, callbackBuilder);
    }

    private void handleOpenSavingsAccountExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            doOpenSavingsAccountRequest((String) messageWrapper.getData(), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void doOpenSavingsAccountRequest(final String iBAN, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/savingsAccount", "iBAN", iBAN,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendOpenSavingsAccountCallback(replyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(replyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendOpenSavingsAccountCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Open savings account request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    private void doCloseSavingsAccountRequest(final String iBAN, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/savingsAccount/close", "iBAN", iBAN,
                (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendCloseSavingsAccountCallback(replyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(replyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendCloseSavingsAccountCallback(final String replyJson, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Close savings account request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(replyJson);
    }

    /**
     * Creates a callbackBuilder for the request so that the result can be sent back to the request source and then
     * calls the exception handler for the request. Replaces a pinCard belonging to a customer.
     * @param callback Used to send the result of the request back to the request source.
     * @param data Parameters from the original request.
     */
    @RequestMapping(value = "/invalidateCard", method = RequestMethod.PUT)
    public void invalidateCard(final Callback<String> callback, @RequestParam("data") final String data) {
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        Map<String, Object> params  = (Map) messageWrapper.getData();
        PinCard pinCard = new PinCard();
        String authToken = (String) params.get("authToken");
        MethodType methodType = messageWrapper.getMethodType();

        pinCard.setAccountNumber((String) params.get("iBAN"));
        pinCard.setCardNumber(Long.parseLong((String) params.get("pinCard")));
        if ((params.get("newPin")).equals("true")) {
            handlePinCardRemovalExceptions(methodType, pinCard, authToken, true, callbackBuilder);
        } else {
            handlePinCardRemovalExceptions(methodType, pinCard, authToken, false, callbackBuilder);
        }
    }

    /**
     * Tries to authenticate the user that sent the request, creates a {@link PinCard} object based on the request
     * json and then forwards the request with the customerId of the user that sent the request.
     * @param methodType Method Type
     * @param pinCard A {@link PinCard} that should be removed from the system.
     * @param authToken Cookie of the user that sent the request.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void handlePinCardRemovalExceptions(final MethodType methodType, final PinCard pinCard,
            final String authToken, final boolean newPin, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(authToken, methodType);
            pinCard.setCustomerId(getCustomerId(authToken));
            doPinCardReplacementRequest(jsonConverter.toJson(pinCard), newPin, callbackBuilder);
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    /**
     * Forwards the pin card replacement request to the pin service, forwards the result to the request source if the
     * request is successful, or sends a rejection to the request source if the request fails.
     * @param pinCardJson Json String representing a {@link PinCard} that should be removed from the system.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void doPinCardReplacementRequest(final String pinCardJson, final boolean newPin,
                                             final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith2Params("/services/pin/invalidateCard",
                "pinCard", pinCardJson, "newPin", newPin, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendPinCardReplacementCallback(JSONParser.removeEscapeCharacters(body), callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Sends the correct callback back to the source.
     * @param jsonReply The new pinCard.
     * @param callbackBuilder Used to send the result of the request back to the request source.
     */
    private void sendPinCardReplacementCallback(final String jsonReply, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Pin card replacement successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonReply);
    }

    @RequestMapping(value = "/systemInformation", method = RequestMethod.PUT)
    public void processSysInfoRequest(final Callback<String> callback, @RequestParam("data") final String request) {
        System.out.printf("%s Received sysInfo admin request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(request), MessageWrapper.class);
        try {
            if (isAdmin(messageWrapper.getMethodType(), messageWrapper.getCookie())) {
                switch (messageWrapper.getMethodType()) {
                    case SIMULATE_TIME:
                        doSimulateTimeRequest(callbackBuilder, messageWrapper);
                        break;
                    case RESET:
                        doResetRequest(callbackBuilder);
                        break;
                    case GET_DATE:
                        doGetDateRequest(callbackBuilder);
                        break;
                    case GET_EVENT_LOGS:
                        doGetEventLogsRequest(callbackBuilder, request);
                        break;
                    default:
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "Internal system error occurred.")));
                        break;
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                        "The user is not authorized to perform this action.",
                        "This user does not seem to have appropriate admin rights.")));
            }
        } catch (SQLException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
        }
    }

    private void doSimulateTimeRequest(final CallbackBuilder callbackBuilder, final MessageWrapper request) {
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/date/increment",
                "days", jsonConverter.toJson(((MetaMethodData) request.getData()).getDays()),
                (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(
                        JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    sendSimulateTimeCallback(callbackBuilder, body);
                } else {
                    System.out.printf("%s, %s", PREFIX, body);
                    callbackBuilder.build().reply(body);
                }
            } else {
                System.out.println("Problem with http in auth");
                System.out.println(body);
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                        "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    private void sendSimulateTimeCallback(final CallbackBuilder callbackBuilder, final String body) {
        System.out.printf("%s Simulate time request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(body);
    }

    private void doResetRequest(final CallbackBuilder callbackBuilder) {
        systemInformationClient.postAsync("/services/systemInfo/reset", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(
                        JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    sendResetCallback(callbackBuilder, body);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                        "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    private void sendResetCallback(final CallbackBuilder callbackBuilder, final String body) {
        System.out.printf("%s Reset request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(body);
    }

    private void doGetDateRequest(final CallbackBuilder callbackBuilder) {
        systemInformationClient.getAsync("/services/systemInfo/date", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = jsonConverter.fromJson(
                        JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    sendDateRequestCallback(callbackBuilder, body);
                } else {
                    callbackBuilder.build().reply(body);
                }
            } else {
                callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                        "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
            }
        });
    }

    private void sendDateRequestCallback(final CallbackBuilder callbackBuilder, final String body) {
        System.out.printf("%s Get Date request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(body);
    }

    private void doGetEventLogsRequest(final CallbackBuilder callbackBuilder, final String request) {
        systemInformationClient.getAsyncWith1Param("/services/systemInfo/log",
                "data", request, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendGetEventLogsCallback(callbackBuilder, body);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendGetEventLogsCallback(final CallbackBuilder callbackBuilder, final String body) {
        System.out.printf("%s Event log query request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(body);
    }

    @RequestMapping(value = "/creditCard", method = RequestMethod.PUT)
    public void processNewCreditCardRequest(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Received new credit card request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleNewCreditCardExceptions(messageWrapper, callbackBuilder);
    }

    private void handleNewCreditCardExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            Long customerId = getCustomerId(messageWrapper.getCookie());
            doNewCreditCardRequest(customerId, (String) messageWrapper.getData(), callbackBuilder);
        } catch (SQLException e) {
            System.out.printf("%s Sql exception, Sending callback.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void doNewCreditCardRequest(final Long customerId, final String accountNumber,
                                        final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Forwarding new credit card request.\n", PREFIX);
        pinClient.putFormAsyncWith1Param("/services/pin/creditCard", "accountNumber",
                accountNumber, (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            CreditCard creditCard = (CreditCard) messageWrapper.getData();
                            try {
                                creditCard.setUsername(getUserNameFromCustomerId(customerId));
                                sendNewCreditCardCallback(creditCard, callbackBuilder);
                            } catch (SQLException | CustomerDoesNotExistException e) {
                                e.printStackTrace();
                            }
                        } else {
                            callbackBuilder.build().reply(replyJson);
                        }
                    } else {
                        System.out.println(httpContentType);
                        System.out.println(httpStatusCode);
                        System.out.println(replyJson);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    @RequestMapping(value = "/savingsAccount/close", method = RequestMethod.PUT)
     public void closeSavingsAccount(final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Received close savings account request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        handleCloseSavingsAccountExceptions(messageWrapper, callbackBuilder);
     }

    private void handleCloseSavingsAccountExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            doCloseSavingsAccountRequest((String) messageWrapper.getData(), callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void sendNewCreditCardCallback(final CreditCard creditCard, final CallbackBuilder callbackBuilder) {
        System.out.printf("%s New credit card request successful, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                false, 200, "Normal Reply", creditCard)));
    }

    @RequestMapping(value = "/setFreezeUserAccount", method = RequestMethod.PUT)
    public void processSetFreezeUserAccountRequest(
            final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Forwarding account link removal.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleSetFreezeUserAccountExceptions(messageWrapper, callbackBuilder);
    }

    private void handleSetFreezeUserAccountExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            if (!isAdmin(messageWrapper.getMethodType(), messageWrapper.getCookie())) {
                throw new UserNotAuthorizedException("Admin rights are required for this action.");
            }
            FreezeAccount freezeAccount = (FreezeAccount) messageWrapper.getData();
            freezeAccount.setCustomerId(getCustomerIdFromUsername(freezeAccount.getUsername()));
            setFreezeUserAccount(freezeAccount, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", "User with username does not appear to exist.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void setFreezeUserAccount(final FreezeAccount freezeAccount, final CallbackBuilder callbackBuilder)
            throws SQLException {
        // do Auth DB update
        SQLConnection con = databaseConnectionPool.getConnection();
        PreparedStatement ps = con.getConnection().prepareStatement(setFreezeStatusAuth);
        ps.setBoolean(1, freezeAccount.getFreeze());
        ps.setLong(2, freezeAccount.getCustomerId());
        ps.executeUpdate();
        ps.close();
        databaseConnectionPool.returnConnection(con);

        // send Pin DB update request
        sendFreezeUserAccountPin(freezeAccount, callbackBuilder);
    }

    private void sendFreezeUserAccountPin(final FreezeAccount freezeAccount, final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith1Param("/services/pin/setFreezeUserAccount", "request",
                jsonConverter.toJson(freezeAccount), (httpStatusCode, httpContentType, removalReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(removalReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            // send Users DB update request
                            sendFreezeUserAccountUsers(freezeAccount, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(removalReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void sendFreezeUserAccountUsers(final FreezeAccount freezeAccount, final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/setFreezeUserAccount", "request",
                jsonConverter.toJson(freezeAccount), (httpStatusCode, httpContentType, removalReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(removalReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            System.out.printf("%s Forwarding setFreezeUserAccount reply.\n", PREFIX);
                        }
                        callbackBuilder.build().reply(removalReplyJson);
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    @RequestMapping(value = "/transferBankAccount", method = RequestMethod.PUT)
    public void processTransferBankAccountRequest(
            final Callback<String> callback, @RequestParam("data") final String data) {
        System.out.printf("%s Forwarding TransferBankAccount request.\n", PREFIX);
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleTransferBankAccountExceptions(messageWrapper, callbackBuilder);
    }

    private void handleTransferBankAccountExceptions(
            final MessageWrapper messageWrapper, final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(messageWrapper.getCookie(), messageWrapper.getMethodType());
            if (!isAdmin(messageWrapper.getMethodType(), messageWrapper.getCookie())) {
                throw new UserNotAuthorizedException("Admin rights are required for this action.");
            }
            AccountLink accountLink = (AccountLink) messageWrapper.getData();
            accountLink.setCustomerId(getCustomerIdFromUsername(accountLink.getUsername()));
            sendTransferBankAccountRequest(accountLink, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (CustomerDoesNotExistException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", "User with username does not appear to exist.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void sendTransferBankAccountRequest(final AccountLink accountLink, final CallbackBuilder callbackBuilder) {
        usersClient.putFormAsyncWith1Param("/services/users/transferBankAccount", "request",
                jsonConverter.toJson(accountLink), (httpStatusCode, httpContentType, data) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            System.out.printf("%s Forwarding TransferBankAccount reply.\n", PREFIX);
                        }
                        callbackBuilder.build().reply(data);
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    @RequestMapping(value = "/transferLimit", method = RequestMethod.PUT)
    public void setTransferLimit(final Callback<String> callback, @RequestParam("cookie") final String cookie,
                                 @RequestParam("iBAN") final String iBAN,
                                 @RequestParam("transferLimit") final Double transferLimit) {
        System.out.printf("%s Forwarding setTransferLimit request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        handleSetTransferLimitExceptions(cookie, iBAN, transferLimit, callbackBuilder);
    }

    private void handleSetTransferLimitExceptions(final String cookie, final String iBAN, final Double transferLimit,
                                                  final CallbackBuilder callbackBuilder) {
        try {
            authenticateRequest(cookie, MethodType.SET_TRANSFER_LIMIT);
            sendSetTransferLimitRequest(iBAN, transferLimit, callbackBuilder);
        } catch (SQLException e) {
            e.printStackTrace();
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to authentication database.")));
        } catch (UserNotAuthorizedException | AccountFrozenException e) {
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 419,
                    "The user is not authorized to perform this action.", e.getMessage())));
        }
    }

    private void sendSetTransferLimitRequest(final String iBAN, final Double transferLimit,
                                             final CallbackBuilder callbackBuilder) {
        systemInformationClient.putFormAsyncWith2Params("/services/systemInfo/transferLimit", "iBAN",
                iBAN, "transferLimit", transferLimit, (httpStatusCode, httpContentType, data) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(data), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            System.out.printf("%s Forwarding setTransferLimit reply.\n", PREFIX);
                        }
                        callbackBuilder.build().reply(data);
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                true, 500, "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Safely shuts down the AuthenticationService.
     */
    public void shutdown() {
        databaseConnectionPool.close();
    }
}
