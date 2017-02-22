package manager;

import com.google.gson.Gson;
import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.http.client.HttpClient;
import util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

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
        String testAccountNumber = "NL52INGB0987890998";
        String testDestinationNumber = "NL52RABO0987890998";

        //Start http client
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(7777).build();
        httpClient.start();
        //doGet(httpClient, testAccountNumber, RequestType.TRANSACTIONHISTORY);
        //doGet(httpClient, testAccountNumber, RequestType.BALANCE);
        //doGet(httpClient, testAccountNumber, RequestType.CUSTOMERDATA);
        //doTransaction(httpClient, testAccountNumber, testDestinationNumber, "De boer",
        //            20.00);
        makeNewAccount(httpClient, "Henk", "De Wilde");
    }

    private static void doTransaction(final HttpClient httpClient, final String sourceAccountNumber,
                               final String destinationAccountNumber, final String destinationAccountHolderName,
                               final double transactionAmount) {
        Transaction transaction = Util.createJsonTransaction(-1, sourceAccountNumber,
                                    destinationAccountNumber, destinationAccountHolderName, transactionAmount,
                                    false, false);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("/services/ui/transaction", "body", gson.toJson(transaction),
                (code, contentType, body) -> { if (code == 200) {
                        System.out.println("received" + body);
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
        Customer customer = Util.createJsonCustomer(newName, newSurname, "", false);
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
        DataRequest request = Util.createJsonRequest(accountNumber, type);
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

}
