package systeminformation;

import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
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
import util.JSONParser;
import util.TableCreator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * Service that handles all system-wide information.
 * Currently tracks the system date.
 */
@RequestMapping("/systemInfo")
class SystemInformationService {
    /** Calendar of the system, used for tracking transactions and validating pin cards. */
    private Calendar myCal;
    /** LocalDate with current date. */
    private LocalDate systemDate;
    /** SystemInformation containing knwon data about the other services. */
    private SystemInformation systemInformation;
    /** Connection to the Ledger service.*/
    private HttpClient apiClient;
    /** Connection to the Ledger service.*/
    private HttpClient authenticationClient;
    /** Connection to the Ledger service.*/
    private HttpClient ledgerClient;
    /** Connection to the Ledger service.*/
    private HttpClient pinClient;
    /** Connection to the Ledger service.*/
    private HttpClient transactionInClient;
    /** Connection to the Ledger service.*/
    private HttpClient transactionOutClient;
    /** Connection to the Ledger service.*/
    private HttpClient usersClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Database connection pool containing persistent database connections. */
    private ConnectionPool databaseConnectionPool;
    /** Prefix used when printing to indicate the message is coming from the SystemInformation Service. */
    private static final String PREFIX = "[SYSINFO]             :";
    /** Map containing dates mapping to lists of transferLimits that need to be set. */
    private Map<LocalDate, LinkedList<TransferLimit>> transferLimitRequests;

    /**
     * Constructor to start the service. This will set the systemDate to the date of the day the method is ran.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     */
    SystemInformationService(final int servicePort, final String serviceHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemDate = LocalDate.now();
        syncCalendar();
        System.out.printf("%s Set date to %s\n", PREFIX, systemDate.toString());
        System.out.printf("%s Current system time %s\n", PREFIX, LocalTime.now(ZoneOffset.UTC).toString());
        this.systemInformation = new SystemInformation();
        this.jsonConverter = new Gson();
        this.databaseConnectionPool = new ConnectionPool();
        this.transferLimitRequests = new HashMap<>();
    }

    @RequestMapping(value = "/newServiceInfo", method = RequestMethod.PUT)
    void processNewSystemInformation(final Callback<String> callback, final @RequestParam("serviceInfo") String body) {
        ServiceInformation serviceInformation = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(body), ServiceInformation.class);
        System.out.printf("%s Received new service information with type: %s\n",
                PREFIX, serviceInformation.getServiceType().toString());
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));

        systemInformation.addNewServiceInformation(serviceInformation);
        if (systemInformation.isComplete()) {
            startAllServices();
        }
    }

    private void startAllServices() {
        System.out.printf("%s System Information now complete, sending start messages to all services\n", PREFIX);

        // SystemInformationService
        startService();

        // ApiService
        startSingleService(apiClient, "api");

        // AuthenticationService
        startSingleService(authenticationClient, "authentication");

        // LedgerService
        startSingleService(ledgerClient, "ledger");

        // PinService
        startSingleService(pinClient, "pin");

        // TransactionReceiveService
        startSingleService(transactionInClient, "transactionReceive");

        // TransactionDispatchService
        startSingleService(transactionOutClient, "transactionDispatch");

        // UsersService
        startSingleService(usersClient, "users");

        System.out.printf("%s Started all services.\n", PREFIX);
    }

    /**
     * Method that initializes all connections to other servers once it knows their addresses.
     */
    private void startService() {
        ServiceInformation api = systemInformation.getApiServiceInformation();
        ServiceInformation authentication = systemInformation.getAuthenticationServiceInformation();
        ServiceInformation ledger = systemInformation.getLedgerServiceInformation();
        ServiceInformation pin = systemInformation.getPinServiceInformation();
        ServiceInformation transactionIn = systemInformation.getTransactionReceiveServiceInformation();
        ServiceInformation transactionOut = systemInformation.getTransactionDispatchServiceInformation();
        ServiceInformation users = systemInformation.getUsersServiceInformation();

        apiClient = httpClientBuilder().setHost(api.getServiceHost())
                .setPort(api.getServicePort()).buildAndStart();
        authenticationClient = httpClientBuilder().setHost(authentication.getServiceHost())
                .setPort(authentication.getServicePort()).buildAndStart();
        ledgerClient = httpClientBuilder().setHost(ledger.getServiceHost())
                .setPort(ledger.getServicePort()).buildAndStart();
        pinClient = httpClientBuilder().setHost(pin.getServiceHost())
                .setPort(pin.getServicePort()).buildAndStart();
        transactionInClient = httpClientBuilder().setHost(transactionIn.getServiceHost())
                .setPort(transactionIn.getServicePort()).buildAndStart();
        transactionOutClient = httpClientBuilder().setHost(transactionOut.getServiceHost())
                .setPort(transactionOut.getServicePort()).buildAndStart();
        usersClient = httpClientBuilder().setHost(users.getServiceHost())
                .setPort(users.getServicePort()).buildAndStart();

        System.out.printf("%s Initialization of System Information service connections complete.\n", PREFIX);
    }

    private void startSingleService(final HttpClient client, final String serviceName) {
        String sysInfo = jsonConverter.toJson(JSONParser.createMessageWrapper(false, 0, "Request", systemInformation));
        System.out.printf("%s Sending ServiceInformation to the " + serviceName + " service.\n", PREFIX);
        client.putFormAsyncWith1Param("/services/" + serviceName + "/start",
                "sysInfo", sysInfo, (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        // This should never happen, since services already sent their info, so they must be running
                        System.err.println("Problem with connection to the " + serviceName + " Service.");
                        System.err.println("Please make sure the " + serviceName + " service is still running");
                        System.err.println("Shutting down.");
                        System.exit(1);
                    }
                });
    }

    /**
     * Increments the systemDate by the amount of days supplied by the requester.
     * @param callback Used to send the result of the request back to the requester.
     * @param days Amount of days to increment the systemDate with.
     */
    @RequestMapping(value = "/date/increment", method = RequestMethod.PUT)
    void incrementDate(final Callback<String> callback, final @RequestParam("days") long days) {
        if (days >= 0) {
            processPassingTime(days, CallbackBuilder.newCallbackBuilder().withStringCallback(callback));
        } else {
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                    true, 418, "One of the parameters has an invalid value.",
                    "Requested amount of days is negative.")));
        }
    }

    private void processPassingTime(final long days, final CallbackBuilder callbackBuilder) {
        int daysInMonth = myCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayOfTheMonth = systemDate.getDayOfMonth();
        syncCalendar();
        if (days >= ((daysInMonth - dayOfTheMonth) + 1)) {
            doInterestProcessingRequest(days, callbackBuilder);
        } else {
            this.systemDate = this.systemDate.plusDays(days);
            doSetTransferLimitsRequest(callbackBuilder);
        }
    }

    private void doInterestProcessingRequest(final long days, final CallbackBuilder callbackBuilder) {
        syncCalendar();
        int daysInMonth = myCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayOfTheMonth = systemDate.getDayOfMonth();
        int firstDayNextMonth = (daysInMonth - dayOfTheMonth) + 1;
        this.systemDate = this.systemDate.plusDays(firstDayNextMonth);
        Long daysLeft = days - firstDayNextMonth;
        ledgerClient.postFormAsyncWith1Param("/services/ledger/interest", "request",
                jsonConverter.toJson(systemDate), (httpStatusCode, httpContentType, body) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                                    JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            doRefillCardsRequest(daysLeft, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(
                                JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    private void doRefillCardsRequest(final Long daysLeft, final CallbackBuilder callbackBuilder) {
        pinClient.putFormAsyncWith1Param("/services/pin/refillCards", "date",
                                        jsonConverter.toJson(systemDate), (httpStatusCode, httpContentType, body) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            int newDaysInMonth = myCal.getActualMaximum(Calendar.DAY_OF_MONTH);
                            int newDayOfTheMonth = systemDate.getDayOfMonth();
                            if (daysLeft >= ((newDaysInMonth - newDayOfTheMonth) + 1)) {
                                doInterestProcessingRequest(daysLeft, callbackBuilder);
                            } else {
                                sendIncrementDaysCallback(callbackBuilder);
                            }
                        } else {
                            callbackBuilder.build().reply(body);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(
                                JSONParser.createMessageWrapper(true, 500,
                                        "An unknown error occurred.",
                                        "There was a problem with one of the HTTP requests")));
                    }
        });
    }

    private void doSetTransferLimitsRequest(final CallbackBuilder callbackBuilder) {
        LinkedList<TransferLimit> limitsToBeProcessed = new LinkedList<>();
        for (LocalDate date : transferLimitRequests.keySet()) {
            if (date.isBefore(systemDate)) {
                limitsToBeProcessed.addAll(transferLimitRequests.remove(date));
            }
        }
        if (limitsToBeProcessed.size() > 0) {
            ledgerClient.putFormAsyncWith1Param("/services/ledger/transferLimit", "limitList",
                    limitsToBeProcessed, (httpStatusCode, httpContentType, body) -> {
                        if (httpStatusCode == HTTP_OK) {
                            MessageWrapper messageWrapper = jsonConverter.fromJson(
                                    JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                            if (!messageWrapper.isError()) {
                                sendIncrementDaysCallback(callbackBuilder);
                            } else {
                                callbackBuilder.build().reply(body);
                            }
                        } else {
                            callbackBuilder.build().reply(jsonConverter.toJson(
                                    JSONParser.createMessageWrapper(true, 500,
                                            "An unknown error occurred.",
                                            "There was a problem with one of the HTTP requests")));
                        }
                    });
        } else {
            sendIncrementDaysCallback(callbackBuilder);
        }

    }

    private void sendIncrementDaysCallback(final CallbackBuilder callbackBuilder) {
        System.out.printf("%s The new system date is %s\n", PREFIX, this.systemDate.toString());
        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(
                                      false, 200, "Normal Reply")));
    }

    private void syncCalendar() {
        myCal = new GregorianCalendar(
                systemDate.getYear(),
                systemDate.getMonth().getValue() - 1,
                systemDate.getDayOfMonth());
    }

    /**
     * Get method for the systemDate.
     * @param callback Used to send the systemDate back to the requester.
     */
    @RequestMapping(value = "/date", method = RequestMethod.GET)
    void getDate(final Callback<String> callback) {
        System.out.printf("%s received date request, sending callback.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply", this.systemDate)));
    }

    /**
     * Reset method for the systemDate & Database.
     * @param callback Used to send the result of the request back to the requester.
     */
    @RequestMapping(value = "/reset", method = RequestMethod.POST)
    void reset(final Callback<String> callback) {
        TableCreator.truncateTables();
        this.systemDate = LocalDate.now();
        System.out.printf("%s Reset request successful, sending callback.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply", this.systemDate)));
    }

    /**
     * Inserts a request into the request log.
     * @param callback Used to send the result back to the request source.
     * @param requestJson Request to insert into the request log.
     */
    @RequestMapping(value = "/log/request", method = RequestMethod.PUT)
    void logRequest(final Callback<String> callback, final @RequestParam("request") String requestJson) {
        JSONRPC2Request request = jsonConverter.fromJson(requestJson, JSONRPC2Request.class);
        System.out.printf("%s Logging new request.\n", PREFIX);
        try {
            addRequestLogToDb(request);
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                    "Normal Reply")));
        } catch (SQLException e) {
            e.printStackTrace();
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                                                "Error connecting to the request log database.")));
        }
    }

    /**
     * Inserts a request into the request log table.
     * @param request Request to insert into the request log.
     * @throws SQLException Thrown when a database error occurs, will cause the request to fail.
     */
    private void addRequestLogToDb(final JSONRPC2Request request) throws SQLException {
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement addRequestLog = databaseConnection.getConnection()
                .prepareStatement(SQLStatements.addRequestLog);
        addRequestLog.setString(1, request.getID().toString());
        addRequestLog.setString(2, request.getMethod());
        StringBuilder paramString = new StringBuilder();
        Map<String, Object> requestParams = request.getNamedParams();
        if (requestParams.keySet().size() > 0) {
            for (String paramName : requestParams.keySet()) {
                paramString.append(paramName);
                paramString.append(":");
                paramString.append(requestParams.get(paramName).toString());
                paramString.append(", ");
            }
            paramString.setLength(paramString.length() - 2);
        }
        addRequestLog.setString(3, paramString.toString());
        addRequestLog.setDate(4, java.sql.Date.valueOf(systemDate));
        addRequestLog.setString(5, LocalTime.now(ZoneOffset.UTC).toString());
        addRequestLog.execute();
        addRequestLog.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Inserts an error into the error log.
     * @param callback Used to send the result of the request back to the request source.
     * @param requestJson Error response to insert into the error log.
     */
    @RequestMapping(value = "/log/error", method = RequestMethod.PUT)
    void errorThing(final Callback<String> callback, final @RequestParam("request") String requestJson) {
        System.out.printf("%s Logging error response.\n\n", PREFIX);
        JSONRPC2Response response = jsonConverter.fromJson(requestJson, JSONRPC2Response.class);
        try {
            addErrorLogToDb(response);
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                    "Normal Reply")));
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("SQLEXCEPTION");
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the error log database.")));
        }
    }

    /**
     * Adds a response containing an error into the error log table.
     * @param response Response containing an error that should be logged in the error log.
     * @throws SQLException Thrown when the connection with the database fails.
     */
    private void addErrorLogToDb(final JSONRPC2Response response) throws SQLException {
        JSONRPC2Error error = response.getError();
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement addErrorLog = databaseConnection.getConnection()
                                                            .prepareStatement(SQLStatements.addErrorLog);
        addErrorLog.setString(1, response.getID().toString());
        addErrorLog.setLong(2, response.getError().getCode());
        addErrorLog.setDate(3, java.sql.Date.valueOf(systemDate));
        String time = LocalTime.now(ZoneOffset.UTC).toString();
        addErrorLog.setString(4, time);
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            errorMessage = "None";
        }
        addErrorLog.setString(5, errorMessage);
        Object errorData = error.getData();
        if (errorData == null) {
            errorData = "None";
        }
        addErrorLog.setString(6, errorData.toString());
        addErrorLog.execute();
        addErrorLog.close();
        databaseConnectionPool.returnConnection(databaseConnection);
    }

    /**
     * Fetches all system logs of a given time span.
     * @param callback Used to send the logs back to the request source.
     * @param request LocalDate objects from the request.
     */
    @RequestMapping(value = "/log", method = RequestMethod.GET)
    void retrieveLogs(final Callback<String> callback, final @RequestParam("data") String request) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(request), MessageWrapper.class);
        try {
            List<Map<String, Object>> logs = fetchLogs(
                    ((MetaMethodData) messageWrapper.getData()).getBeginDate(),
                    ((MetaMethodData) messageWrapper.getData()).getEndDate());
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                    "Normal Reply", logs)));
        } catch (SQLException e) {
            e.printStackTrace();
            callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500,
                    "Error connecting to the error log database.")));
        }
    }

    /**
     * Fetches all request and error logs from the database and returns them as a list.
     * @param beginDate LocalDate that marks the beginning of the time span.
     * @param endDate LocalDate that marks the end of the time span.
     * @return List containing maps for every log in the request and error log tables.
     * @throws SQLException Thrown when the database connection fails.
     */
    private List<Map<String, Object>> fetchLogs(final LocalDate beginDate, final LocalDate endDate)
            throws SQLException {
        List<Map<String, Object>> logs = new LinkedList<>();
        SQLConnection databaseConnection = databaseConnectionPool.getConnection();
        PreparedStatement getErrorLogs = databaseConnection.getConnection()
                                                            .prepareStatement(SQLStatements.getErrorLogs);
        getErrorLogs.setDate(1, java.sql.Date.valueOf(beginDate));
        getErrorLogs.setDate(2, java.sql.Date.valueOf(endDate));
        ResultSet errorLogs = getErrorLogs.executeQuery();
        while (errorLogs.next()) {
            Map<String, Object> errorMap = new HashMap<>();
            LocalDate errorDate = errorLogs.getDate("date").toLocalDate();
            String errorTime = errorLogs.getString("time");
            errorMap.put("timeStamp", createTimestamp(errorDate, errorTime));
            String eventLog = "[Error " + errorLogs.getLong("error_code") + "]: Request "
                    + errorLogs.getString("request_id")
                    + " caused an error error with the following error message: "
                    + errorLogs.getString("message") + " and the following data: "
                    + errorLogs.getString("data");
            errorMap.put("eventLog", eventLog);
            logs.add(errorMap);
        }
        getErrorLogs.close();
        PreparedStatement getRequestLogs = databaseConnection.getConnection()
                                                            .prepareStatement(SQLStatements.getRequestLogs);
        getRequestLogs.setDate(1, java.sql.Date.valueOf(beginDate));
        getRequestLogs.setDate(2, java.sql.Date.valueOf(endDate));
        ResultSet requestLogs = getRequestLogs.executeQuery();
        while (requestLogs.next()) {
            Map<String, Object> requestMap = new HashMap<>();
            LocalDate requestDate = requestLogs.getDate("date").toLocalDate();
            String requestTime = requestLogs.getString("time");
            requestMap.put("timeStamp", createTimestamp(requestDate, requestTime));
            String eventLog = "[Request " + requestLogs.getString("request_id") + "]: "
                    + requestLogs.getString("method") + " request was made with the following parameters: "
                    + requestLogs.getString("params");
            requestMap.put("eventLog", eventLog);
            logs.add(requestMap);
        }
        return logs;
    }

    /**
     * Adds a TransferLimit to the list of TransferLimit requests, and sets it to be executed in 1 day.
     * @param callback Used to send the result of the request back to the request source.
     * @param iBAN iBAN of the account the TransferLimit should be set for.
     * @param transferLimit TransferLimit that should be set.
     */
    @RequestMapping(value = "/TransferLimit", method = RequestMethod.PUT)
    void setTransferLimit(final Callback<String> callback, final @RequestParam("iBAN") String iBAN,
                      final @RequestParam("TransferLimit") Double transferLimit) {
        System.out.printf("%s Received set transfer limit request.\n", PREFIX);
        LocalDate dayOfExecution = systemDate.plusDays(1L);
        LinkedList<TransferLimit> requestsOnDay = transferLimitRequests.get(dayOfExecution);
        if (requestsOnDay == null) {
            requestsOnDay = new LinkedList<>();
        }
        requestsOnDay.add(new TransferLimit(iBAN, transferLimit));
        transferLimitRequests.put(dayOfExecution, requestsOnDay);
        System.out.printf("%s Successfully added set transfer limit request to queue.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply")));
    }

    /**
     * Creates a timestamp for a given date and time.
     * @param date Date for the timestamp.
     * @param time Time for the timestamp.
     * @return Timestamp for a date time combination.
     */
    private String createTimestamp(final LocalDate date, final String time) {
        return date.toString() + "T" + time + "Z";
    }

}
