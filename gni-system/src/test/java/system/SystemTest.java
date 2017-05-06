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
import util.TableCreator;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * System test that starts up all services and executes all requests and prints their process flow.
 * @author Noel
 * @version 1
 */
public class SystemTest {

    private static String cookie = "1:LTYzNzg4NDUzNTE2MzMzMjc2";
    private static PinCard pinCard = new PinCard();
    private static String accountNumber = "";
    private static String externalAccountNumber = "NL00IIIB5695575206";
    private static Long customerId = null;
    private static String userName = "henkdeboer";
    private static String password = "henkdeboer";
    private static String PREFIX = "[Test]                :";

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
        TableCreator.truncateTable();
        Sys.sleep(1000);
        initializeServices();
        Sys.sleep(1000);
        //test variables
        //String ownAccountNumber = "NL00GNIB4134192911";
        String deboerNumber = "NL00GNIB4633920918";
        Long deboerId = 1L;

        //Start http client
        HttpClient uiClient = httpClientBuilder().setHost("localhost").setPort(9990).build();
        uiClient.start();
        HttpClient externalBankClient = httpClientBuilder().setHost("localhost").setPort(9994).build();
        externalBankClient.start();
        HttpClient pinClient = httpClientBuilder().setHost("localhost").setPort(9995).build();
        pinClient.start();
        Sys.sleep(2000);
        //reset database
        doNewCustomerRequest(uiClient, "H.", "Henk", "de Boer", "hdb@kpn.planet.nl",
        "061212121212", "Batslaan 25", "20-04-1992",
        new Long("12"),100000, 100000, userName, password);
        Sys.sleep(2000);
        doLogin(uiClient, userName, password);
        Sys.sleep(2000);
        doNewPinCardRequest(uiClient, accountNumber);
        Sys.sleep(2000);
        doPin(pinClient, pinCard, externalAccountNumber, "De Wilde", 20.00);
        Sys.sleep(2000);
        doPinCardRemovalRequest(uiClient, pinCard);
        Sys.sleep(2000);
        /*doNewAccountRequest(uiClient, cookie);
        Sys.sleep(2000);
        doGet(uiClient, "", RequestType.ACCOUNTS, cookie);
        Sys.sleep(2000);
        doAccountLinkRequest(uiClient, batsId, batsNumber, cookie);
        Sys.sleep(2000);
        doExternalTransaction(externalBankClient, externalAccountNumber, deboerNumber, "H. de Boer",
                "Moneys",2000.00);
                Sys.sleep(2000);
        doInternalTransaction(uiClient, deboerNumber, externalAccountNumber, "De Boer",
                "moar moneys",2.00, cookie);*/
        Sys.sleep(2000);
        doGet(uiClient, deboerNumber, RequestType.TRANSACTIONHISTORY, cookie);
        Sys.sleep(2000);
        doGet(uiClient, deboerNumber, RequestType.BALANCE, cookie);
        Sys.sleep(2000);
        doGet(uiClient, deboerNumber, RequestType.CUSTOMERDATA, cookie);
        Sys.sleep(2000);
        TableCreator.truncateTable();
    }

    private static void initializeServices() {
        LedgerServiceMain.main();
        UsersServiceMain.main();
        UIServiceMain.main();
        AuthenticationServiceMain.main();
        PinServiceMain.main();
        TransactionDispatchServiceMain.main();
        TransactionReceiveServiceMain.main();
        System.out.println("\n\n\n");
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
        System.out.printf("%s Sending internal transaction.\n", PREFIX);
        httpClient.putFormAsyncWith2Params("/services/ui/transaction", "request",
                gson.toJson(transaction), "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            long transactionId = reply.getTransactionID();
                            System.out.printf("%s Internal transaction %d successfull.\n\n\n\n",
                                    PREFIX, transactionId);
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Internal transaction couldn't be processed\n\n\n\n", PREFIX);
                        } else {
                            System.out.printf("%s Internal transaction was not successfull\n\n\n\n", PREFIX);
                        }
                    } else {
                        System.out.printf("%s Transaction request failed.\n\n\n\n", PREFIX);
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
        System.out.printf("%s Sending external transaction.\n", PREFIX);
        httpClient.putFormAsyncWith1Param("/services/transactionReceive/transaction", "request",
                gson.toJson(transaction), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            long transactionId = reply.getTransactionID();
                            System.out.printf("%s Transaction %d successfull.\n\n\n", PREFIX, transactionId);
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Transaction couldn't be processed.\n\n\n", PREFIX);
                        } else {
                            System.out.printf("%s Transaction was not successfull\n\n\n", PREFIX);
                        }
                    } else {
                        System.out.printf("%s Transaction request failed.\n\n\n", PREFIX);
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
        System.out.printf("%s Sending new customer request.\n", PREFIX);
        uiClient.putFormAsyncWith1Param("/services/ui/customer", "customer", gson.toJson(customer),
                (code, contentType, body) -> { if (code == HTTP_OK) {
                    Customer reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Customer.class);
                    System.out.printf("%s Customer successfully created in the system.\n\n\n\n", PREFIX);
                    customerId = reply.getCustomerId();
                    accountNumber = reply.getAccount().getAccountNumber();
                } else {
                    System.out.printf("%s Customer creation request failed, body: %s\n\n\n\n", PREFIX, body);
                }
                });
    }

    /**
     * Sends a get request to the UI service to retrieve information from other services.
     * @param uiClient Client connected to the UI service.
     * @param accountNumber Accountnumber of the customer we want to request information for.
     * @param type Type of request we want to do{@link RequestType}.
     */
    private static void doGet(final HttpClient uiClient, final String accountNumber, final RequestType type,
                              final String cookie) {
        DataRequest request = JSONParser.createJsonRequest(accountNumber, type, 0L);
        Gson gson = new Gson();
        System.out.printf("%s Sending data request of type %s\n", PREFIX, type.toString());
        uiClient.getAsyncWith2Params("/services/ui/data", "request", gson.toJson(request),
                "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        switch (type) {
                            case BALANCE:
                                DataReply balanceReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        DataReply.class);
                                System.out.printf("%s Request successfull, balance: %f\n\n\n\n", PREFIX,
                                        balanceReply.getAccountData().getBalance());
                                break;
                            case TRANSACTIONHISTORY:
                                DataReply transactionReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        DataReply.class);
                                System.out.printf("%s Transaction history request successfull, history:\n", PREFIX);
                                for (Transaction x : transactionReply.getTransactions()) {
                                    System.out.printf("%s %s \n", PREFIX, x.toString());
                                }
                                System.out.println("\n\n\n");
                                break;
                            case CUSTOMERDATA:
                                Customer customerReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        Customer.class);
                                System.out.printf("%s Request successfull, Name: %s, dob: %s\n\n\n\n", PREFIX,
                                        customerReply.getInitials() + customerReply.getSurname(),
                                        customerReply.getDob());
                                break;
                            case ACCOUNTS:
                                DataReply accountsReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                        DataReply.class);
                                System.out.printf("%s Request successfull, accounts: %s\n\n\n\n", PREFIX,
                                        accountsReply.getAccounts());
                                break;
                            default:
                                System.out.printf("%s Couldn't get reply data.\n\n\n", PREFIX);
                                break;
                        }
                    } else {
                        System.out.printf("%s Request not successfull, body: %s\n", PREFIX, body);
                    }
                });
    }

    /**
     * Sends a transaction request to the Pin service, simulating a Pin transaction of a customer.
     * @param pinClient Client connected to the Pin service.
     * @param pinCard PinCard used in the transaction.
     * @param destinationAccountNumber Account number to transfer funds into.
     * @param destinationAccountHolderName Name of the owner of the destination account number.
     * @param transactionAmount Amount to transfer.
     */
    private static void doPin(final HttpClient pinClient, final PinCard pinCard,
                              final String destinationAccountNumber, final String destinationAccountHolderName,
                              final double transactionAmount) {
        PinTransaction pin = JSONParser.createJsonPinTransaction(pinCard.getAccountNumber(), destinationAccountNumber,
                destinationAccountHolderName, pinCard.getPinCode(), pinCard.getCardNumber(), transactionAmount);
        Gson gson = new Gson();
        System.out.printf("%s Sending pin transaction.\n", PREFIX);
        pinClient.putFormAsyncWith1Param("/services/pin/transaction", "request", gson.toJson(pin),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Transaction reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Transaction.class);
                        if (reply.isSuccessful() && reply.isProcessed()) {
                            System.out.printf("%s Pin transaction successfull.\n\n\n", PREFIX);
                        } else if (!reply.isProcessed()) {
                            System.out.printf("%s Pin transaction couldn't be processed.\n\n\n", PREFIX);
                        } else {
                            System.out.printf("%s Pin transaction was not successfull.\n\n\n", PREFIX);
                        }
                    } else {
                        System.out.printf("%s Pin transaction request failed.\n\n\n", PREFIX);
                        System.out.println(body);
                    }
                });
    }

    //TODO update format when new protocol arrives
    private static void doAccountLinkRequest(final HttpClient uiClient, final String accountNumber,
                                             final String cookie) {
        AccountLink request = JSONParser.createJsonAccountLink(accountNumber, 0L);
        Gson gson = new Gson();
        System.out.printf("%s Sending account link request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/account", "request", gson.toJson(request),
                "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        AccountLink reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), AccountLink.class);
                        if (reply.isSuccessfull()) {
                            System.out.printf("%s Account link successfull for Account Holder: %s, AccountNumber: %s\n\n\n\n",
                                    PREFIX, reply.getCustomerId(), reply.getAccountNumber());
                        } else {
                            System.out.printf("%s Account link creation unsuccessfull.\n\n\n\n", PREFIX);
                        }
                    } else {
                        System.out.printf("%s Account link creation failed.\n\n\n\n", PREFIX);
                    }
                });
    }

    //TODO update format when new protocol arrives
    private static void doNewAccountRequest(final HttpClient uiClient, final String cookie) {
        Customer accountOwner = JSONParser.createJsonCustomer("M.S.", "Mats", "Bats",
                "mats@bats.nl", "0656579876",
                "Batslaan 35", "20-04-1889",
                new Long("1234567890"), 0,
                0, 0L,  "matsbats",
                "matsbats");
        Gson gson = new Gson();
        System.out.printf("%s Sending new account request.\n", PREFIX);
        uiClient.putFormAsyncWith2Params("/services/ui/account/new", "request",
                gson.toJson(accountOwner), "cookie", cookie,
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Customer reply = gson.fromJson(JSONParser.removeEscapeCharacters(body), Customer.class);
                        System.out.printf("%s New Account creation successfull, Account Holder: %s, AccountNumber: %s\n\n\n\n",
                                PREFIX, reply.getCustomerId(), reply.getAccount().getAccountNumber());
                    } else {
                        System.out.printf("%s Account creation failed. body: %s\n\n\n\n", PREFIX, body);
                    }
                });
    }

    private static void doLogin(final HttpClient uiClient, final String username, final String password) {
        Authentication authentication = JSONParser.createJsonAuthenticationLogin(username, password);
        Gson gson = new Gson();
        System.out.printf("%s Logging in.\n", PREFIX);
        uiClient.putFormAsyncWith1Param("/services/ui/login", "authData", gson.toJson(authentication),
                (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        Authentication authenticationReply = gson.fromJson(JSONParser.removeEscapeCharacters(body),
                                Authentication.class);
                        System.out.printf("%s Successfull login, set the following cookie: %s\n\n\n\n",
                                PREFIX, authenticationReply.getCookie());
                        cookie = authenticationReply.getCookie();
                    } else {
                        System.out.printf("%s Login failed.\n\n\n\n", PREFIX);
                    }
                });
    }

    private static void doNewPinCardRequest(final HttpClient uiClient, final String accountNumber) {
        Gson gson = new Gson();
        uiClient.putFormAsyncWith2Params("/services/ui/card", "accountNumber", accountNumber,
                "cookie", cookie, (code, contentType, body) -> {
            if (code == HTTP_OK) {
                PinCard newPinCard = gson.fromJson(JSONParser.removeEscapeCharacters(body), PinCard.class);
                System.out.printf("%s Successfully requested a new pin card.\n\n\n\n", PREFIX);
                pinCard = newPinCard;
            } else {
                System.out.printf("%s New pin card request failed.\n\n\n\n", PREFIX);
            }
        });
    }

    private static void doPinCardRemovalRequest(final HttpClient uiClient, final PinCard pinCardtoRemove) {
        Gson gson = new Gson();
        uiClient.sendAsyncRequestWith2Params("/services/ui/card", "DELETE", "pinCard",
                gson.toJson(pinCardtoRemove), "cookie", cookie, (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        PinCard removedPinCard = gson.fromJson(JSONParser.removeEscapeCharacters(body), PinCard.class);
                        System.out.printf("%s Successfully removed pin card#%s.\n\n\n\n", PREFIX,
                                          removedPinCard.getCardNumber());
                        pinCard = null;
                    } else {
                        System.out.printf("%s Pin card removal failed.\n\n\n\n", PREFIX);
                    }
                });
    }
}
