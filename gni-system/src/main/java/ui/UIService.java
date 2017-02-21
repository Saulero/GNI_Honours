package ui;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.Listen;
import io.advantageous.qbit.http.client.HttpClient;
import util.*;
import queue.ServiceManager;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 5-2-17.
 * Interface that outside users can use to view their balance, transaction
 * history, and make transactions.
 */
public final class UIService {
    private HttpClient httpClient;

    public UIService(String host, int port) {
        /* Setup an httpClient. */
        httpClient = httpClientBuilder()
                .setHost(host).setPort(port).build();
        httpClient.start();
    }
    //TODO method to remove customer from system
    /**
     * Send a transaction request to the ledger service that will reply with
     * the transaction history of the account.
     * @param accountNumber Account number to request the transaction history
     *                      for.
     */
    //TODO handle request failure
    public void requestTransactionHistory(final String accountNumber) {
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.TRANSACTIONHISTORY);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                ((code, contentType, body) -> { if (code == 200) {
                    DataReply reply = gson.fromJson(body, DataReply.class);
                    if (reply.getAccountNumber().equals(accountNumber)
                                                                && reply.getType() == RequestType.TRANSACTIONHISTORY) {
                        System.out.println("Request successfull, data: " +  reply.getData());
                    } else {
                        System.out.println("Transaction request failed.");
                    }
                } else {
                    System.out.println("Transaction request failed.");
                } }));
    }

    /**
     * Sends a balance request to the ledger service for an account.
     * @param accountNumber Account number to request the balance for
     */
    //TODO handle request failure
    public void requestBalance(final String accountNumber) {
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.BALANCE);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                ((code, contentType, body) -> { if (code == 200) {
                    DataReply reply = gson.fromJson(body, DataReply.class);
                    if (reply.getAccountNumber().equals(accountNumber)
                            && reply.getType() == RequestType.BALANCE) {
                        System.out.println("Balance request successfull, data: " +  reply.getData());
                    } else {
                        System.out.println("Balance request failed.");
                    }
                } else {
                    System.out.println("Balance request failed.");
                } }));
    }


    /**
     * Sends a customer data request to the user service for an account.
     * @param accountNumber Account number to request the balance for
     */
    //TODO needs to be reworked to not be dependent on accountNumber
    public void requestCustomerData(final String accountNumber) {
        DataRequest request = Util.createJsonRequest(accountNumber, RequestType.CUSTOMERDATA);
        Gson gson = new Gson();
        httpClient.getAsyncWith1Param("/services/user/data", "body", gson.toJson(request),
                ((code, contentType, body) -> { if (code == 200) {
                    DataReply reply = gson.fromJson(body, DataReply.class);
                    if (reply.getAccountNumber().equals(accountNumber)
                            && reply.getType() == RequestType.CUSTOMERDATA) {
                        System.out.println("Customer information request successfull, data: " +  reply.getData());
                    } else {
                        System.out.println("Customer information request failed.");
                    }
                } else {
                    System.out.println("Customer information request failed.");
                } }));
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
        //Do transaction work
        System.out.printf("UI: Executed new transaction\n\n");

        Transaction transaction = Util.createJsonTransaction(transactionNumber, sourceAccountNumber,
                                                             destinationAccountNumber, destinationAccountHolderName,
                                                             amount, false, false);
        Gson gson = new Gson();
        httpClient.putFormAsyncWith1Param("/services/user/transaction", "body", gson.toJson(transaction),
                ((code, contentType, body) -> { if (code == 200) {
                    Transaction reply = gson.fromJson(body, Transaction.class);
                    if (reply.equals(transaction) && reply.isSuccessfull() && reply.isProcessed()) {
                        System.out.println("Transaction request successfull.");
                    } else {
                        System.out.println("Transaction request failed.");
                    }
                } else {
                    System.out.println("Transaction request failed.");
                } }));
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


