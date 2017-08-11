package transactionin;

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
 * Receives transaction requests from external banks, send them to the ledger
 * for processing, and sends the confirmation/failure back to the external bank.
 */
@RequestMapping("/transactionReceive")
class TransactionReceiveService {
    /** Connection to the Ledger service.*/
    private HttpClient ledgerClient;
    /** Connection to the SystemInformation service. */
    private HttpClient systemInformationClient;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the Transaction Receive Service. */
    private static final String PREFIX = "[TransactionReceive]  :";

    /**
     * Constructor.
     * @param servicePort Port that this service is running on.
     * @param serviceHost Host that this service is running on.
     * @param sysInfoPort Port the System Information Service can be found on.
     * @param sysInfoHost Host the System Information Service can be found on.
     */
    TransactionReceiveService(final int servicePort, final String serviceHost,
               final int sysInfoPort, final String sysInfoHost) {
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
                servicePort, serviceHost, ServiceType.TRANSACTION_RECEIVE_SERVICE);
        System.out.printf("%s Sending ServiceInformation to the SystemInformationService.\n", PREFIX);
        systemInformationClient.putFormAsyncWith1Param("/services/systemInfo/newServiceInfo",
                "serviceInfo", serviceInfo, (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode != HTTP_OK) {
                        System.err.println("Problem with connection to the SystemInformationService.");
                        System.err.println("Shutting down the Transaction Receive service.");
                        System.exit(1);
                    }
                });
    }

    /**
     * Method that initializes all connections to other services once it knows their addresses.
     * @param callback Callback to the source of the request.
     * @param body Json string containing the request that was made.
     */
    @RequestMapping(value = "/start", method = RequestMethod.POST)
    public void startService(final Callback<String> callback, final String body) {
        MessageWrapper messageWrapper = jsonConverter.fromJson(
                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);

        SystemInformation sysInfo = (SystemInformation) messageWrapper.getData();
        ServiceInformation ledger = sysInfo.getLedgerServiceInformation();

        this.ledgerClient = httpClientBuilder().setHost(ledger.getServiceHost())
                .setPort(ledger.getServicePort()).buildAndStart();

        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
    }

    /**
     * Processes transactions that come from external banks by checking if the destination is a GNIB accountNumber
     * and then executing the transaction. Reports the result back to the request source.
     * @param callback Used to send a reply back to the external bank.
     * @param transactionRequestJson Json String representing an incoming transaction.
     */
    //TODO might need reworking when it is clear how external transactions will be sent
    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processIncomingTransaction(final Callback<String> callback,
                                           final @RequestParam("request") String transactionRequestJson) {
        System.out.printf("%s Received incoming transaction request.\n", PREFIX);
        CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder().withStringCallback(callback);
        doIncomingTransactionRequest(transactionRequestJson, callbackBuilder);
    }

    /**
     * Sends a transaction request to the LedgerService for executing and then processes the reply
     * and reports the result back to the request source.
     * @param transactionRequestJson JSON String representing the transaction that needs to be sent
     * @param callbackBuilder Used to send the result back to the bank that requested the transaction.
     */
    private void doIncomingTransactionRequest(final String transactionRequestJson,
                                              final CallbackBuilder callbackBuilder) {
        ledgerClient.putFormAsyncWith1Param("/services/ledger/transaction/in", "request",
                transactionRequestJson, (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = jsonConverter.fromJson(JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            processIncomingTransactionReply((Transaction) messageWrapper.getData(), transactionReplyJson, callbackBuilder);
                        } else {
                            callbackBuilder.build().reply(transactionReplyJson);
                        }
                    } else {
                        System.out.printf("%s Received a rejection from ledger, sending rejection.\n", PREFIX);
                        callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests4")));
                    }
                });
    }

    private void processIncomingTransactionReply(final Transaction transaction, final String transactionReplyJson,
                                                 final CallbackBuilder callbackBuilder) {
        if (transaction.isProcessed() && transaction.isSuccessful()) {
            sendIncomingTransactionRequestCallback(transactionReplyJson, callbackBuilder);
            //TODO send reply to external bank.
        } else {
            System.out.printf("%s Transaction unsuccessful, sending rejection.\n", PREFIX);
            callbackBuilder.build().reply(jsonConverter.toJson(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred.")));
        }
    }

    private void sendIncomingTransactionRequestCallback(final String transactionReplyJson,
                                                        final CallbackBuilder callbackBuilder) {
        System.out.printf("%s Successfully processed incoming transaction, sending callback.\n", PREFIX);
        callbackBuilder.build().reply(transactionReplyJson);
    }
}
