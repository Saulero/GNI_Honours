import io.advantageous.qbit.QBit;
import io.advantageous.qbit.events.EventManager;
import static io.advantageous.boon.core.Sys.sleep;

/**
 * Created by noel on 4-2-17.
 * Microservices manager, handles the event queue and starts the microservices.
 */
class ServiceManager {
    static final String USER_CREATION_CHANNEL = "org.gni.user.new";
    static final String TRANSACTION_REQUEST_CHANNEL = "org.gni.transaction.request";
    static final String TRANSACTION_PROCESSING_CHANNEL = "org.gni.transaction.process";
    static final String TRANSACTION_VERIFICATION_CHANNEL = "org.gni.transaction.verify";
    static final String DATA_REQUEST_CHANNEL = "org.gni.data.request";
    static final String DATA_REPLY_CHANNEL = "org.gni.data.reply";

    public static void main(String[] args) {
        //test variables
        String testAccountNumber = "NL52INGB0987890998";
        String testDestinationNumber = "NL52RABO0987890998";

        //Create eventmanager and start microservices
        EventManager eventManager = QBit.factory().systemEventManager();
        UserService userService = new UserService();
        LedgerService ledgerService = new LedgerService();
        UIService uiService = new UIService();
        TransactionDispatchService dispatchService = new TransactionDispatchService();
        TransactionReceiveService receiveService = new TransactionReceiveService();

        //Set listener on microservices
        eventManager.listen(userService);
        eventManager.listen(ledgerService);
        eventManager.listen(uiService);
        eventManager.listen(dispatchService);
        eventManager.listen(receiveService);

        //Emulate user using the uiService
        //TODO move Service calls to the services themselves. Does not work at the moment because there is no code to call the methods.
        System.out.println("Manager: Creating customer");
        uiService.createCustomer("freek", "de wilde", "NL52INGB0987890998");
        sleep(200);

        System.out.println("Manager: Requesting customer info");
        uiService.requestCustomerData(testAccountNumber);
        sleep(200);

        System.out.println("Manager: Requesting customer balance");
        uiService.requestBalance(testAccountNumber);
        sleep(200);

        System.out.println("Manager: Requesting transaction history");
        uiService.requestTransactionHistory(testAccountNumber);
        sleep(200);

        System.out.println("Manager: Creating transaction");
        Transaction transaction = new Transaction(testAccountNumber, 250, testDestinationNumber,  "de wilde", "112");
        eventManager.send(TRANSACTION_REQUEST_CHANNEL, transaction);
        sleep(200);

        System.out.println("Manager: Creating transaction");
        Transaction transaction2 = new Transaction(testAccountNumber, -250, testDestinationNumber,  "de wilde", "112");
        eventManager.send(TRANSACTION_REQUEST_CHANNEL, transaction2);
        sleep(200);

        System.out.println("Manager: Requesting customer balance");
        uiService.requestBalance(testAccountNumber);
        sleep(200);

        //Test method.
        System.out.println("Manager: Printing ledger:");
        ledgerService.printLedger();
    }
}
