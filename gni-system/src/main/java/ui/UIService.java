package ui;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * Created by noel on 5-2-17.
 * Interface that outside users can use to view their balance, transaction
 * history, and make transactions.
 */
@RequestMapping("/ui")
public final class UIService {

    //TODO method to remove customer from system
    /**
     * Send a transaction request to the ledger service that will reply with
     * the transaction history of the account.
     * @param accountNumber Account number to request the transaction history
     *                      for.
     */
    //TODO handle request failure
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void requestTransactionHistory(final Callback<String> callback, @RequestParam("body") final String accountNumber) {
        /* Setup a httpClient. */
        System.out.printf("Setting host %s, port %d", "localhost", 8888);
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.TRANSACTIONHISTORY);
        Gson gson = new Gson();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        System.out.println("UI: Sending request to Users");
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                                     (code, contentType, body) -> { if (code == 200) {
                    String replyJson = body.substring(1, body.length() - 1).replaceAll("\\\\", "");
                    DataReply reply = gson.fromJson(replyJson, DataReply.class);
                                             System.out.println("processed");
                    if (reply.getAccountNumber().equals(accountNumber)
                                                                && reply.getType() == RequestType.TRANSACTIONHISTORY) {
                        callbackBuilder.build().reply(gson.toJson(reply));
                        System.out.println("Request successfull, data: " +  reply.getData());
                    } else {
                        System.out.println("Transaction request failed on reply check.");
                        callbackBuilder.build().reject("Transaction request failed.");
                    }
                } else {
                    System.out.println("Transaction history request failed.");
                                         System.out.println(body);
                                         System.out.println(code);
                    callbackBuilder.build().reject("Transaction history request failed.");
                } });
    }

    /**
     * Sends a balance request to the ledger service for an account.
     * @param accountNumber Account number to request the balance for
     */
    //TODO handle request failure
    public void requestBalance(final String accountNumber) {
        System.out.printf("Setting host %s, port %d", "localhost", 8888);
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.BALANCE);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                                     (code, contentType, body) -> { if (code == 200) {
                    DataReply reply = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                                    DataReply.class);
                    if (reply.getAccountNumber().equals(accountNumber)
                            && reply.getType() == RequestType.BALANCE) {
                        System.out.println("Balance request successfull, data: " +  reply.getData());
                    } else {
                        System.out.println("Balance request failed.");
                    }
                } else {
                    System.out.println("Balance request failed.");
                                         System.out.println(body);
                } });
    }


    /**
     * Sends a customer data request to the user service for an account.
     * @param accountNumber Account number to request the balance for
     */
    //TODO needs to be reworked to not be dependent on accountNumber
    public void requestCustomerData(final String accountNumber) {
        System.out.printf("Setting host %s, port %d", "localhost", 8888);
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.CUSTOMERDATA);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                                     (code, contentType, body) -> { if (code == 200) {
                    DataReply reply = gson.fromJson(body, DataReply.class);
                    if (reply.getAccountNumber().equals(accountNumber)
                            && reply.getType() == RequestType.CUSTOMERDATA) {
                        System.out.println("Customer information request successfull, data: " +  reply.getData());
                    } else {
                        System.out.println("Customer information request failed.");
                    }
                } else {
                    System.out.println("Customer information request failed.");
                } });
    }

    /**
     * Send a transaction request to the transaction out service.
     * @param sourceAccountNumber Account number to draw the funds from
     * @param amount Amount of money to send
     * @param destinationAccountNumber Account Number to send the money to
     * @param destinationAccountHolderName The name of the owner of the
     *                                     destination account number
     * @param transactionNumber Transaction number used for processing
     */
    //TODO PUT request
    //TODO generate transaction number in ledger, process reply
    public void doTransaction(final String sourceAccountNumber,
                              final double amount,
                              final String destinationAccountNumber,
                              final String destinationAccountHolderName,
                              final long transactionNumber) {
        System.out.printf("Setting host %s, port %d", "localhost", 8888);
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        //Do transaction work
        System.out.printf("UI: Executed new transaction\n\n");

        Transaction transaction = Util.createJsonTransaction(transactionNumber, sourceAccountNumber,
                                                             destinationAccountNumber, destinationAccountHolderName,
                                                             amount, false, false);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("/services/user/transaction", "body", gson.toJson(transaction),
                                         (code, contentType, body) -> { if (code == 200) {
                    Transaction reply = gson.fromJson(body, Transaction.class);
                    if (reply.equals(transaction) && reply.isSuccessfull() && reply.isProcessed()) {
                        System.out.println("Transaction request successfull.");
                    } else {
                        System.out.println("Transaction request failed.");
                    }
                } else {
                    System.out.println("Transaction request failed.");
                } });
    }

    /**
     * Send a user creation request over the USER_CREATION_CHANNEL to create
     * a new user in the system.
     * @param name First name of the user to create
     * @param surname Surname of the user to create
     * @param accountNumber Account number of the user to create
     */
    //TODO PUT request
    //TODO Account number needs to be requested from the ledger
    public void createCustomer(final String name, final String surname,
                               final String accountNumber) {
        System.out.printf("Setting host %s, port %d", "localhost", 8888);
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(8888).build();
        httpClient.start();
        System.out.printf("UI: Creating customer with name: %s %s\n\n", name,
                        surname);
        Customer customer = Util.createJsonCustomer(name, surname, accountNumber, false);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("services/user/customer", "body", gson.toJson(customer),
                                         (code, contentType, body) -> { if (code == 200) {
                    Customer reply = gson.fromJson(body, Customer.class);
                    if (reply.getEnrolled() && reply.getAccountNumber().equals(customer.getAccountNumber())
                            && reply.getName().equals(customer.getName()) && reply.getSurname().equals(
                                    customer.getSurname())) {
                        System.out.println("Customer successfully enrolled.");
                    } else {
                        System.out.println("Customer enrollment error, see data: " + reply);
                    }
                } else {
                    System.out.println("Customer enrollment failed, connection error.");
                }
                });
    }
}


