package manager;

import com.google.gson.Gson;
import io.advantageous.qbit.http.client.HttpClient;
import databeans.*;
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
        String testAccountNumber = "NL52RABO0987890998";
        String testDestinationNumber = "NL52GNIB0987890998";

        //Start http client
        HttpClient userClient = httpClientBuilder().setHost("localhost").setPort(9990).build();
        userClient.start();
        HttpClient externalBankClient = httpClientBuilder().setHost("localhost").setPort(9994).build();
        externalBankClient.start();
        HttpClient pinClient = httpClientBuilder().setHost("localhost").setPort(9995).build();
        pinClient.start();
        //doGet(httpClient, testAccountNumber, RequestType.TRANSACTIONHISTORY);
        //doGet(httpClient, testAccountNumber, RequestType.BALANCE);
        //doGet(httpClient, testAccountNumber, RequestType.CUSTOMERDATA);
        doPin(pinClient, testDestinationNumber, testAccountNumber, "De wilde", "8888",
                "730", 20.00);
        //doTransaction(externalBankClient, testAccountNumber, testDestinationNumber, "De boer",
        //        20.00, true);
        //doTransaction(httpClient, testAccountNumber, testDestinationNumber, "De boer",
        //            20.00, false);
        //makeNewAccount(httpClient, "Henk", "De Wilde");
    }

    private static void doTransaction(final HttpClient httpClient, final String sourceAccountNumber,
                               final String destinationAccountNumber, final String destinationAccountHolderName,
                               final double transactionAmount, boolean isExternal) {
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
                if (reply.isSuccessfull() && reply.isProcessed()) {
                    System.out.println("Transaction successfull.");
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
        Customer customer = JSONParser.createJsonCustomer(newName, newSurname, "", false);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("/services/ui/customer", "body", gson.toJson(customer),
                (code, contentType, body) -> { if (code == 200) {
                    Customer reply = gson.fromJson(body.substring(1, body.length() - 1)
                                                        .replaceAll("\\\\", ""), Customer.class);
                    if (reply.getEnrolled()) {
                        System.out.println("Customer successfully created in the system.");
                        System.out.println("Your account number is: " + reply.getAccountNumber());
                    } else {
                        System.out.println("Customer couldnt be created in the system.");
                    }
                } else {
                    System.out.println("Customer creation request failed, body: " + body);
                }
            });
    }

    private static void doGet(HttpClient httpClient, String accountNumber, RequestType type) {
        DataRequest request = JSONParser.createJsonRequest(accountNumber, type);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/ui/data", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == 200) {
                        System.out.println("received" + body);
                        DataReply reply = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                DataReply.class);
                        System.out.println("Request successfull, reply: " + reply.getData());
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
                        if (reply.isSuccessfull() && reply.isProcessed()) {
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
