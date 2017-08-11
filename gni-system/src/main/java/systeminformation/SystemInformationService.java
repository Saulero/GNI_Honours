package systeminformation;

import com.google.gson.Gson;
import databeans.MessageWrapper;
import databeans.ServiceInformation;
import databeans.SystemInformation;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;
import util.TableCreator;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;

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
    /** Prefix used when printing to indicate the message is coming from the SystemInformation Service. */
    private static final String PREFIX = "[SYSINFO]             :";

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
        this.systemInformation = new SystemInformation();
        this.jsonConverter = new Gson();
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
            sendIncrementDaysCallback(callbackBuilder);
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
                        System.out.println(httpStatusCode);
                        System.out.println(body);
                        callbackBuilder.build().reply(jsonConverter.toJson(
                                JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests")));
                    }
                });
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
}
