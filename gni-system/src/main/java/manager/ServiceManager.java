package manager;

import com.google.gson.Gson;
import databeans.Customer;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.PinTransaction;
import databeans.RequestType;
import io.advantageous.qbit.http.client.HttpClient;
import ledger.Transaction;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Created by noel on 4-2-17.
 * @author Noel
 * @version 1
 * Microservices manager, handles the event manager and starts the microservices.
 */
public final class ServiceManager {
    /**
     * Private constructor to satisfy utility class property.
     */
    private ServiceManager() {
        //Not called
    }

    /**
     * Initializes the eventmanager and then starts all services and sets up
     * their listeners.
     * @param args empty argument
     */
    public static void main(final String[] args) {
        //test variables
        String testAccountNumber = "NL00GNIB5695575206";
        String testDestinationNumber = "NL52GNIB0987890998";
        String deWildeNumber = "NL00GNIB5695575206";

        //Start http client
        HttpClient userClient = httpClientBuilder().setHost("localhost").setPort(9990).build();
        userClient.start();
        HttpClient externalBankClient = httpClientBuilder().setHost("localhost").setPort(9994).build();
        externalBankClient.start();
        //HttpClient pinClient = httpClientBuilder().setHost("localhost").setPort(9995).build();
        //pinClient.start();
        //doPin(pinClient, testDestinationNumber, testAccountNumber, "De wilde", "8888",
        //        "730", 20.00);
        makeNewAccount(userClient, "Mats", "Bats");
        //doTransaction(externalBankClient, testAccountNumber, "NL00GNIB5695575206", "De Wilde",
        //        200.00, true);
        //doTransaction(userClient, deWildeNumber, testDestinationNumber, "De Boer",
        //            250.00, false);
        //doGet(userClient, testAccountNumber, RequestType.TRANSACTIONHISTORY);
        //doGet(userClient, testAccountNumber, RequestType.BALANCE);
        //doGet(userClient, testAccountNumber, RequestType.CUSTOMERDATA);
    }

    private static void doTransaction(final HttpClient httpClient, final String sourceAccountNumber,
                               final String destinationAccountNumber, final String destinationAccountHolderName,
                               final double transactionAmount, final boolean isExternal) {
        Transaction transaction = JSONParser.createJsonTransaction(-1, sourceAccountNumber,
                                    destinationAccountNumber, destinationAccountHolderName, transactionAmount,
                                    false, false);
        Gson gson = new Gson();
        String uri;
        if (isExternal) {
            uri = "/services/transactionReceive/transaction";
        } else {
            uri = "/services/ui/transaction";
        }
        httpClient.putFormAsyncWith1Param(uri, "body", gson.toJson(transaction),
                                        (code, contentType, body) -> {
            if (code == 200) {
                Transaction reply = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                                    Transaction.class);
                if (reply.isSuccessful() && reply.isProcessed()) {
                    long transactionId = reply.getTransactionID();
                    System.out.printf("Transaction %d successfull.\n", transactionId);
                } else if (!reply.isProcessed()) {
                    System.out.println("Transaction couldn't be processed");
                } else {
                    System.out.println("Transaction was not successfull");
                }
            } else {
                System.out.println("Transaction request failed, body: " + body);
            }
        });
    }

    private static void makeNewAccount(final HttpClient httpClient, final String newName, final String newSurname) {
        Customer customer = JSONParser.createJsonCustomer("M.", newName, newSurname, "bob@bob.com",
                                    "0612121212121", "berlin 25", "20-04-1889",
                                    new Long("2345678981231231"), 0, 0);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("/services/ui/customer", "body", gson.toJson(customer),
                (code, contentType, body) -> { if (code == 200) {
                    Customer reply = gson.fromJson(body.substring(1, body.length() - 1)
                                                        .replaceAll("\\\\", ""), Customer.class);
                        System.out.println("Customer successfully created in the system.");
                } else {
                    System.out.println("Customer creation request failed, body: " + body);
                }
            });
    }

    private static void doGet(final HttpClient httpClient, final String accountNumber, final RequestType type) {
        DataRequest request = JSONParser.createJsonRequest(accountNumber, type);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/ui/data", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == 200) {
                        DataReply reply = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                DataReply.class);
                        switch (type) {
                            case BALANCE:
                                System.out.printf("Request successful, balance: %f\n",
                                        reply.getAccountData().getBalance());
                                break;
                            case TRANSACTIONHISTORY:
                                for (Transaction x : reply.getTransactions()) {
                                    System.out.println(x.toString());
                                }
                                break;
                            case CUSTOMERDATA:
                                System.out.println(reply.getData());
                                break;
                            default:
                                System.out.println("couldnt get reply data.");
                                break;
                        }
                    } else {
                        System.out.println("Transaction history request not successfull, body: " + body);
                    }
                });
    }

    private static void doPin(final HttpClient httpClient, final String sourceAccountNumber,
                              final String destinationAccountNumber, final String destinationAccountHolderName,
                              final String pinCode, final String cardNumber, final double transactionAmount) {
        PinTransaction pin = JSONParser.createJsonPinTransaction(sourceAccountNumber, destinationAccountNumber,
                                destinationAccountHolderName, pinCode, cardNumber, transactionAmount);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("/services/pin/transaction", "body", gson.toJson(pin),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.println("Pin transaction successfull.");
                        } else if (!reply.isProcessed()) {
                            System.out.println("Pin transaction couldn't be processed");
                        } else {
                            System.out.println("Pin transaction was not successfull");
                        }
                    } else {
                        System.out.println("Pin transaction request failed, body: " + body);
                    }
                });
    }

}
