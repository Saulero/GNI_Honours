package system;

import authentication.AuthenticationServiceMain;
import com.google.gson.Gson;
import databeans.*;
import io.advantageous.boon.core.Sys;
import io.advantageous.qbit.http.client.HttpClient;
import ledger.LedgerServiceMain;
import pin.PinServiceMain;
import transactionin.TransactionReceiveServiceMain;
import transactionout.TransactionDispatchServiceMain;
import ui.UIServiceMain;
import users.UsersServiceMain;
import util.JSONParser;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * System test that starts up all services and executes all requests and prints their process flow.
 * @author Noel
 * @version 1
 */
public class SystemTest {

    private static String cookie = "1:LTYzNzg4NDUzNTE2MzMzMjc2";
    private static String prefix = "[Test]                :";

    /**
     * Private constructor for utility class.
     */
    private SystemTest() {
        //Not called
    }

    /**
     * Calls services using http clients to simulate a client using the system.
     * @param args should be empty argument
     */
    public static void main(final String[] args) {
        LedgerServiceMain.main();
        UsersServiceMain.main();
        UIServiceMain.main();
        AuthenticationServiceMain.main();
        PinServiceMain.main();
        TransactionDispatchServiceMain.main();
        TransactionReceiveServiceMain.main();
        System.out.println("\n\n\n");
        Sys.sleep(1000);
        //test variables
        String externalNumber = "NL00IIIB5695575206";
        String ownAccountNumber = "NL00GNIB4134192911";
        Long batsId = 1L;

        //Start http client
        HttpClient uiClient = httpClientBuilder().setHost("localhost").setPort(9990).build();
        uiClient.start();
        HttpClient externalBankClient = httpClientBuilder().setHost("localhost").setPort(9994).build();
        externalBankClient.start();
        HttpClient pinClient = httpClientBuilder().setHost("localhost").setPort(9995).build();
        pinClient.start();
        Sys.sleep(2000);
        doLogin(uiClient, "test", "test");
        Sys.sleep(2000);
        /*doNewCustomerRequest(uiClient, "test", "test", "test", "mats@bats.nl",
                "061212121212", "Batslaan 25", "20-04-1889",
                new Long("1234567890"),1000, 100000, "test",
                "test");
          Sys.sleep(2000);*/
        //doNewAccountRequest(uiClient, batsId);
        //Sys.sleep(2000);
        doGet(uiClient, "", RequestType.ACCOUNTS, batsId, cookie);
        Sys.sleep(2000);
        //doAccountLinkRequest(uiClient, batsId, batsNumber, cookie);
        //Sys.sleep(1000);
        doPin(pinClient, ownAccountNumber, externalNumber, "De wilde", "8888",
                "730", 20.00);
        Sys.sleep(2000);
        doExternalTransaction(externalBankClient, externalNumber, ownAccountNumber, "Bats",
                "Moneys",2000.00);
        Sys.sleep(2000);
        doInternalTransaction(uiClient, ownAccountNumber, externalNumber, "De Boer",
                "moar moneys",2.00, cookie);
        Sys.sleep(2000);
        doGet(uiClient, ownAccountNumber, RequestType.TRANSACTIONHISTORY, batsId, cookie);
        Sys.sleep(2000);
        doGet(uiClient, ownAccountNumber, RequestType.BALANCE, batsId, cookie);
        Sys.sleep(2000);
        doGet(uiClient, ownAccountNumber, RequestType.CUSTOMERDATA, batsId, cookie);
    }

    /**
     * Performs a transaction by calling the transaction receive service or the ui service.
     * @param httpClient Client connected to the service to be called.
     * @param sourceAccountNumber Source account for the transaction.
     * @param destinationAccountNumber Destination account for the transaction.
     * @param destinationAccountHolderName Name of the destination account holder.
     * @param transactionAmount Amount to transfer.
     */
    private static void doInternalTransaction(final HttpClient httpClient, final String sourceAccountNumber,
                                              final String destinationAccountNumber, final String destinationAccountHolderName,
                                              final String description, final double transactionAmount, final String cookie) {
        Transaction transaction = JSONParser.createJsonTransaction(-1, sourceAccountNumber,
                destinationAccountNumber, destinationAccountHolderName, description,
                transactionAmount,false, false);
        Gson gson = new Gson();
        System.out.printf("%s Sending internal transaction.\n", prefix);
        httpClient.putFormAsyncWith2Params("/services/ui/transaction", "request",
                gson.toJson(transaction), "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            long transactionId = reply.getTransactionID();
                            System.out.printf("%s Internal transaction %d successfull.\n\n\n\n",
                                    prefix, transactionId);
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Internal transaction couldn't be processed\n\n\n\n", prefix);
                        } else {
                            System.out.printf("%s Internal transaction was not successfull\n\n\n\n", prefix);
                        }
                    } else {
                        System.out.printf("%s Transaction request failed.\n\n\n\n", prefix);
                    }
                });
    }

    /**
     * Performs a transaction by calling the transaction receive service or the ui service.
     * @param httpClient Client connected to the service to be called.
     * @param sourceAccountNumber Source account for the transaction.
     * @param destinationAccountNumber Destination account for the transaction.
     * @param destinationAccountHolderName Name of the destination account holder.
     * @param transactionAmount Amount to transfer.
     */
    private static void doExternalTransaction(final HttpClient httpClient, final String sourceAccountNumber,
                                              final String destinationAccountNumber,
                                              final String destinationAccountHolderName,
                                              final String description, final double transactionAmount) {
        Transaction transaction = JSONParser.createJsonTransaction(-1, sourceAccountNumber,
                destinationAccountNumber, destinationAccountHolderName, description,
                transactionAmount,false, false);
        Gson gson = new Gson();
        System.out.printf("%s Sending external transaction.\n", prefix);
        httpClient.putFormAsyncWith1Param("/services/transactionReceive/transaction", "request",
                gson.toJson(transaction), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            long transactionId = reply.getTransactionID();
                            System.out.printf("%s Transaction %d successfull.\n\n\n", prefix, transactionId);
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Transaction couldn't be processed.\n\n\n", prefix);
                        } else {
                            System.out.printf("%s Transaction was not successfull\n\n\n", prefix);
                        }
                    } else {
                        System.out.printf("%s Transaction request failed.\n\n\n", prefix);
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
                                             final double spendingLimit, final double balance, final String username,
                                             final String password) {
        //todo make sure customer cant set his own balance/spendingLimit
        Customer customer = JSONParser.createJsonCustomer(initials, name, surname, email, telephoneNumber, address, dob,
                ssn, spendingLimit, balance, new Long("0"),
                username, password);
        Gson gson = new Gson();
        System.out.printf("%s Sending new customer request.\n", prefix);
        uiClient.putFormAsyncWith1Param("/services/ui/customer", "customer", gson.toJson(customer),
                (code, contentType, body) -> { if (code == HTTP_OK) {
                    Customer reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Customer.class);
                    System.out.printf("%s Customer successfully created in the system.\n\n\n\n", prefix);
                } else {
                    System.out.printf("%s Customer creation request failed, body: %s\n\n\n\n", prefix, body);
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
                              final Long userId, final String cookie) {
        DataRequest request = JSONParser.createJsonRequest(accountNumber, type, userId);
        Gson gson = new Gson();
        System.out.printf("%s Sending data request of type %s\n", prefix, type.toString());
        uiClient.getAsyncWith2Params("/services/ui/data", "request", gson.toJson(request),
                "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        switch (type) {
                            case BALANCE:
                                DataReply balanceReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        DataReply.class);
                                System.out.printf("%s Request successfull, balance: %f\n\n\n\n", prefix,
                                        balanceReply.getAccountData().getBalance());
                                break;
                            case TRANSACTIONHISTORY:
                                DataReply transactionReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        DataReply.class);
                                System.out.printf("%s Transaction history request successfull, history: ", prefix);
                                for (Transaction x : transactionReply.getTransactions()) {
                                    System.out.printf("%s %s ", prefix, x.toString());
                                }
                                System.out.println("\n\n\n");
                                break;
                            case CUSTOMERDATA:
                                Customer customerReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        Customer.class);
                                System.out.printf("%s Request successfull, Name: %s, dob: %s\n\n\n\n", prefix,
                                        customerReply.getInitials() + customerReply.getSurname(),
                                        customerReply.getDob());
                                break;
                            case ACCOUNTS:
                                DataReply accountsReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        DataReply.class);
                                System.out.printf("%s Request successfull, accounts: %s\n\n\n\n", prefix,
                                        accountsReply.getAccounts());
                                break;
                            default:
                                System.out.printf("%s Couldn't get reply data.\n\n\n", prefix);
                                break;
                        }
                    } else {
                        System.out.printf("%s Request not successfull, body: %s\n", prefix, body);
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
        System.out.printf("%s Sending pin transaction.\n", prefix);
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "request", gson.toJson(pin),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.printf("%s Pin transaction successfull.\n\n\n", prefix);
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Pin transaction couldn't be processed.\n\n\n", prefix);
                        } else {
                            System.out.printf("%s Pin transaction was not successfull.\n\n\n", prefix);
                        }
                    } else {
                        System.out.printf("%s Pin transaction request failed.\n\n\n", prefix);
                        System.out.println(body);
                    }
                });
    }

    private static void doAccountLinkRequest(final HttpClient uiClient, final Long customerId,
                                             final String accountNumber, final String cookie) {
        AccountLink request = JSONParser.createJsonAccountLink(accountNumber, customerId);
        Gson gson = new Gson();
        System.out.printf("%s Sending account link request.\n", prefix);
        uiClient.putFormAsyncWith2Params("/services/ui/account", "request", gson.toJson(request),
                "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        AccountLink reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), AccountLink.class);
                        if (reply.isSuccessfull()) {
                            System.out.printf("%s Account link successfull for Account Holder: %s, AccountNumber: %s\n\n\n\n",
                                    prefix, reply.getCustomerId(), reply.getAccountNumber());
                        } else {
                            System.out.printf("%s Account link creation unsuccessfull.\n\n\n\n", prefix);
                        }
                    } else {
                        System.out.printf("%s Account link creation failed.\n\n\n\n", prefix);
                    }
                });
    }

    private static void doNewAccountRequest(final HttpClient uiClient, final Long customerId) {
        Customer accountOwner = JSONParser.createJsonCustomer("M.S.", "Mats", "Bats",
                "mats@bats.nl", "0656579876",
                "Batslaan 35", "20-04-1889",
                new Long("1234567890"), 0,
                0, customerId,  "matsbats",
                "matsbats");
        Gson gson = new Gson();
        System.out.printf("%s Sending new account request.\n", prefix);
        uiClient.putFormAsyncWith1Param("/services/ui/account/new", "request", gson.toJson(accountOwner),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Customer reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Customer.class);
                        System.out.printf("%s New Account creation successfull, Account Holder: %s, AccountNumber: %s\n\n\n\n",
                                prefix, reply.getId(), reply.getAccount().getAccountNumber());
                    } else {
                        System.out.printf("%s Account creation failed. body: %s\n\n\n\n", prefix, body);
                    }
                });
    }

    private static void doLogin(final HttpClient uiClient, final String username, final String password) {
        Authentication authentication = JSONParser.createJsonAuthenticationLogin(username, password);
        Gson gson = new Gson();
        System.out.printf("%s Logging in.\n", prefix);
        uiClient.putFormAsyncWith1Param("/services/ui/login", "authData", gson.toJson(authentication),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Authentication authenticationReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                Authentication.class);
                        System.out.printf("%s Successfull login, set the following cookie: %s\n\n\n\n",
                                prefix, authenticationReply.getCookie());
                        cookie = authenticationReply.getCookie();
                    } else {
                        System.out.printf("%s Login failed.\n\n\n\n", prefix);
                    }
                });
    }
}
