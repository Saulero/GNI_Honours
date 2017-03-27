package manager;

import com.google.gson.Gson;
import databeans.*;
import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.http.client.HttpClient;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 * @version 1
 * Currently used to do system tests, will be rewritten to start all services.
 */
//TODO rewrite to start services
public final class ServiceManager {
    /**
     * Private constructor for utility class.
     */
    private ServiceManager() {
        //Not called
    }

    /**
     * Calls services using http clients to simulate a client using the system.
     * @param args empty argument
     */
    public static void main(final String[] args) {
        //test variables
        String testAccountNumber = "NL00GNIB5695575206";
        String testDestinationNumber = "NL52GNIB0987890998";
        String batsNumber = "NL02GNIB0516754934";
        Long batsId = 2L;

        //Start http client
        HttpClient uiClient = httpClientBuilder().setHost("localhost").setPort(9990).build();
        uiClient.start();
        HttpClient externalBankClient = httpClientBuilder().setHost("localhost").setPort(9994).build();
        externalBankClient.start();
        HttpClient pinClient = httpClientBuilder().setHost("localhost").setPort(9995).build();
        pinClient.start();
        Sys.sleep(1000);
        doGet(uiClient, "", RequestType.ACCOUNTS, batsId);
        doAccountLinkRequest(uiClient, batsId, batsNumber);
        doAccountCreation(uiClient, batsId);
        doPin(pinClient, batsNumber, testAccountNumber, "De wilde", "8888",
                "730", 20.00);
        doNewCustomerRequest(uiClient, "test", "test", "test", "mats@bats.nl",
                "061212121212", "Batslaan 25", "20-04-1889",
                new Long("1234567890"),1000, 0);
        doTransaction(externalBankClient, testAccountNumber, batsNumber, "Bats",
                "Moneys",200.00, true);
        doTransaction(uiClient, testAccountNumber, testDestinationNumber, "De Boer",
                "moar moneys",250.00, false);
        doGet(uiClient, batsNumber, RequestType.TRANSACTIONHISTORY, batsId);
        doGet(uiClient, testAccountNumber, RequestType.BALANCE, batsId);
        doGet(uiClient, testAccountNumber, RequestType.CUSTOMERDATA, batsId);
    }

    /**
     * Performs a transaction by calling the transaction receive service or the ui service.
     * @param httpClient Client connected to the service to be called.
     * @param sourceAccountNumber Source account for the transaction.
     * @param destinationAccountNumber Destination account for the transaction.
     * @param destinationAccountHolderName Name of the destination account holder.
     * @param transactionAmount Amount to transfer.
     * @param isExternal Boolean indicating if the transaction is from an external bank(so to the transaction receive
     *                   service) or from a user (so to the ui service).
     */
    private static void doTransaction(final HttpClient httpClient, final String sourceAccountNumber,
                               final String destinationAccountNumber, final String destinationAccountHolderName,
                               final String description, final double transactionAmount, final boolean isExternal) {
        Transaction transaction = JSONParser.createJsonTransaction(-1, sourceAccountNumber,
                                    destinationAccountNumber, destinationAccountHolderName, description,
                                    transactionAmount,false, false);
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
                Transaction reply = gson.fromJson(JSONParser.sanitizeJson(body), Transaction.class);
                if (reply.isSuccessful() && reply.isProcessed()) {
                    long transactionId = reply.getTransactionID();
                    System.out.printf("Transaction %d successfull.\n", transactionId);
                } else if (!reply.isProcessed()) {
                    System.out.println("Transaction couldn't be processed");
                } else {
                    System.out.println("Transaction was not successfull");
                }
            } else {
                System.out.println("Transaction request failed.");
            }
        });
    }

    /**
     * Sends a customer creation request to the UI service.
     * @param uiClient Client connected to the UI.
     * @param initials Initials of the customer.
     * @param name First name of the customer.
     * @param surname Last name of the customer.
     * @param email Email of the customer.
     * @param telephoneNumber Telephone number of the customer.
     * @param address Address of the customer.
     * @param dob Date of birth of the customer.
     * @param ssn Social security number of the customer.
     * @param spendingLimit Spendinglimit of the account.
     * @param balance Balance of the account.
     */
    private static void doNewCustomerRequest(final HttpClient uiClient, final String initials, final String name,
                                             final String surname, final String email, final String telephoneNumber,
                                             final String address, final String dob, final Long ssn,
                                             final double spendingLimit, final double balance) {
        Customer customer = JSONParser.createJsonCustomer(initials, name, surname, email, telephoneNumber, address, dob,
                                                            ssn, spendingLimit, balance);
        Gson gson = new Gson();
        uiClient.putFormAsyncWith1Param("/services/ui/customer", "body", gson.toJson(customer),
                (code, contentType, body) -> { if (code == 200) {
                    Customer reply = gson.fromJson(JSONParser.sanitizeJson(body), Customer.class);
                        System.out.println("Customer successfully created in the system.");
                } else {
                    System.out.println("Customer creation request failed, body: " + body);
                }
            });
    }

    /**
     * Sends a get request to the UI service to retrieve information from other services.
     * @param uiClient Client connected to the UI service.
     * @param accountNumber Accountnumber of the customer we want to request information for.
     * @param type Type of request we want to do{@link RequestType}.
     * @param userId Id of the customer we want to request information for.
     */
    private static void doGet(final HttpClient uiClient, final String accountNumber, final RequestType type,
                              final Long userId) {
        DataRequest request = JSONParser.createJsonRequest(accountNumber, type, userId);
        Gson gson = new Gson();
        uiClient.getAsyncWith1Param("/services/ui/data", "body", gson.toJson(request),
                (code, contentType, body) -> {
                    if (code == 200) {
                        switch (type) {
                            case BALANCE:
                                DataReply balanceReply = gson.fromJson(JSONParser.sanitizeJson(body), DataReply.class);
                                System.out.printf("Request successfull, balance: %f\n",
                                        balanceReply.getAccountData().getBalance());
                                break;
                            case TRANSACTIONHISTORY:
                                DataReply transactionReply = gson.fromJson(JSONParser.sanitizeJson(body),
                                                                            DataReply.class);
                                for (Transaction x : transactionReply.getTransactions()) {
                                    System.out.println(x.toString());
                                }
                                break;
                            case CUSTOMERDATA:
                                Customer customerReply = gson.fromJson(JSONParser.sanitizeJson(body), Customer.class);
                                System.out.printf("Request successfull, Name: %s, dob: %s\n",
                                                            customerReply.getInitials() + customerReply.getSurname(),
                                                            customerReply.getDob());
                                break;
                            case ACCOUNTS:
                                DataReply accountsReply = gson.fromJson(JSONParser.sanitizeJson(body), DataReply.class);
                                System.out.printf("Request successfull, accounts: %s\n", accountsReply.getAccounts());
                                break;
                            default:
                                System.out.println("couldnt get reply data.");
                                break;
                        }
                    } else {
                        System.out.println("Request not successfull, body: " + body);
                    }
                });
    }

    /**
     * Sends a transaction request to the Pin service, simulating a Pin transaction of a customer.
     * @param pinClient Client connected to the Pin service.
     * @param sourceAccountNumber Account number to transfer funds from.
     * @param destinationAccountNumber Account number to transfer funds into.
     * @param destinationAccountHolderName Name of the owner of the destination account number.
     * @param pinCode Pin code of the source account owner.
     * @param cardNumber Card number used in the transaction.
     * @param transactionAmount Amount to transfer.
     */
    private static void doPin(final HttpClient pinClient, final String sourceAccountNumber,
                              final String destinationAccountNumber, final String destinationAccountHolderName,
                              final String pinCode, final String cardNumber, final double transactionAmount) {
        PinTransaction pin = JSONParser.createJsonPinTransaction(sourceAccountNumber, destinationAccountNumber,
                destinationAccountHolderName, pinCode, cardNumber, transactionAmount);
        Gson gson = new Gson();
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "body", gson.toJson(pin),
                                        (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Transaction reply = gson.fromJson(JSONParser.sanitizeJson(body), Transaction.class);
                if (reply.isSuccessful() && reply.isProcessed()) {
                    System.out.println("Pin transaction successfull.");
                } else if (!reply.isProcessed()) {
                    System.out.println("Pin transaction couldn't be processed");
                } else {
                    System.out.println("Pin transaction was not successfull");
                }
            } else {
                System.out.println("Pin transaction request failed.");
            }
        });
    }

    private static void doAccountLinkRequest(final HttpClient uiClient, final Long customerId,
                                             final String accountNumber) {
        AccountLink request = JSONParser.createJsonAccountLink(accountNumber, customerId);
        Gson gson = new Gson();
        uiClient.putFormAsyncWith1Param("/services/ui/account", "body", gson.toJson(request),
                                            (code, contentType, body) -> {
            if (code == HTTP_OK) {
                AccountLink reply = gson.fromJson(JSONParser.sanitizeJson(body), AccountLink.class);
                if (reply.isSuccessfull()) {
                    System.out.printf("Account link successfull for Account Holder: %s, AccountNumber: %s\n",
                                                                    reply.getCustomerId(), reply.getAccountNumber());
                } else {
                    System.out.println("Account link creation unsuccessfull.");
                }
            } else {
                System.out.println("Account link creation failed.");
            }
        });
    }

    private static void doAccountCreation(final HttpClient uiClient, final Long customerId) {
        Customer accountOwner = JSONParser.createJsonCustomer("M.S.", "Mats", "Bats",
                                                              "mats@bats.nl", "0656579876",
                                                              "Batslaan 35", "20-04-1889",
                                                               new Long("1234567890"), 0,
                                                              0, customerId);
        Gson gson = new Gson();
        uiClient.putFormAsyncWith1Param("/services/ui/account/new", "body", gson.toJson(accountOwner),
                                                                                    (code, contentType, body) -> {
            if (code == HTTP_OK) {
                Customer reply = gson.fromJson(JSONParser.sanitizeJson(body), Customer.class);
                System.out.printf("New Account creation successfull, Account Holder: %s, AccountNumber: %s\n",
                                  reply.getId(), reply.getAccount().getAccountNumber());
            } else {
                System.out.println("Account creation failed.");
                System.out.println(body);
            }
        });
    }
}
