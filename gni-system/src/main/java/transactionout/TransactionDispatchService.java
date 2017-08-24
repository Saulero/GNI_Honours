package transactionout;

import com.google.gson.Gson;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 2
 * Receives outgoing transaction requests.
 * Sends these requests to the ledger for processing.
 * Handles the response from the ledger and sends the transaction to its
 * respective receiving bank.
 */
@RequestMapping("/transactionDispatch")
class TransactionDispatchService {
    /** Connection to the Ledger service. */
    private HttpClient ledgerClient;
    /** Connection to the SystemInformation service. */
    private HttpClient systemInformationClient;
    /** Used for Json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Transaction Dispatch Service. */
    private static final String PREFIX = "[TransactionDispatch] :";

    /**
     * Constructor.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    TransactionDispatchService(final int servicePort, final String serviceHost,
               final int sysInfoPort, final String sysInfoHost) {
        System.out.printf("%s Service started on the following location: %s:%d.\n", PREFIX, serviceHost, servicePort);
        this.systemInformationClient = httpClientBuilder().setHost(sysInfoHost).setPort(sysInfoPort).buildAndStart();
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
                servicePort, serviceHost, ServiceType.TRANSACTION_DISPATCH_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", jsonConverter.toJson(serviceInfo), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        System.err.println("Problem with connection to the SystemInformationService.");
                        System.err.println("Shutting down the Transaction Dispatch service.");
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

        this.ledgerClient = httpClientBuilder().setHost(ledger.getServiceHost())
                .setPort(ledger.getServicePort()).buildAndStart();

        System.out.printf("%s Initialization of Transaction Dispatch service connections complete.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
    }

    /**
     * Creates a callback builder for the transaction request, and then forwards it to the ledger.
     * @param callback Callback used to send a reply back to the origin of the request.
     * @param transactionRequestJson Json String containing a Transaction object that should be executed
     *                               {@link Transaction}.
     * @param customerId The ID of the customer that made the transaction
     */
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback,
                                          @RequestParam("request") final String transactionRequestJson,
                                          @RequestParam("customerId") final String customerId,
                                          @RequestParam("override") final Boolean override) {
        Transaction request = jsonConverter.fromJson(transactionRequestJson, Transaction.class);
        System.out.printf("%s Transaction received, sourceAccount: %s ,destAccount: %s, amount: %.2f, customerId %s\n",
                PREFIX, request.getSourceAccountNumber(), request.getDestinationAccountNumber(),
                            request.getTransactionAmount(), customerId);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doOutgoingTransactionRequest(transactionRequestJson, customerId, override, callbackBuilder);
    }

    /**
     * Forwards a transaction request to the ledger for execution, and processes the reply if successful, sends a
     * rejection to the service that sent the transaction request if the ledger request fails.
     * @param transactionRequestJson Json String representing a transaction that the ledger should execute
     *                               {@link Transaction}.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void doOutgoingTransactionRequest(final String transactionRequestJson, final String customerId,
                                              final boolean override, final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith3Params("/services/ledger/transaction/out", "request",
                transactionRequestJson, "customerId", customerId, "override", override,
                (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Transaction transaction = (Transaction) messageWrapper.getData();
                            processOutgoingTransactionReply(transaction, transactionReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(transactionReplyJson);
                        }
                    } else {
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }

    /**
     * Checks if the transaction is processed and successful, if it is forwards the reply to the requesting service,
     * if it is not sends a rejection to the requesting service.
     * @param transaction A transaction that the ledger tried to execute {@link Transaction}.
     * @param transactionReplyJson The original JSON.
     * @param callbackBuilder Used to send the received reply back to the source of the request.
     */
    private void processOutgoingTransactionReply(final Transaction transaction, final String transactionReplyJson,
                                                 final CallbackBuilder callbackBuilder) {
        if (transaction.isProcessed() && transaction.isSuccessful()) {
            //TODO send outgoing transaction.
            System.out.printf("%s Successful transaction, sending callback.\n", PREFIX);
            callbackBuilder.build().reply(transactionReplyJson);
        } else {
            callbackBuilder.build().reply(transactionReplyJson);
        }
    }
}
