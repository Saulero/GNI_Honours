package queue;

import io.advantageous.qbit.QBit;
import io.advantageous.qbit.events.EventManager;
import ledger.Ledger;
import util.Transaction;
import transactionin.TransactionReceiveService;
import transactionout.TransactionDispatchService;
import ui.UIService;
import users.UserService;

import static io.advantageous.boon.core.Sys.sleep;

/**
 * Created by noel on 4-2-17.
 * @author Noel
 * @version 1
 * Microservices manager, handles the event queue and starts the microservices.
 */
public final class ServiceManager {
    /** Channel to send user creation requests over using Customer objects. */
    public static final String USER_CREATION_CHANNEL = "org.gni.user.new";

    /** Channel to send transaction requests over using a Transaction object. */
    public static final String TRANSACTION_REQUEST_CHANNEL
                                                = "org.gni.transaction.request";

    /** Channel used by transaction dispatch services to send
     * Transaction objects to the ledger. */
    public static final String TRANSACTION_PROCESSING_CHANNEL
                                                = "org.gni.transaction.process";

    /** Channel used by the ledger to send processed Transaction
     * objects back to the dispatch services. */
    public static final String TRANSACTION_VERIFICATION_CHANNEL
                                                = "org.gni.transaction.verify";

    /** Channel to request data over using DataRequest objects. */
    public static final String DATA_REQUEST_CHANNEL = "org.gni.data.request";

    /** Channel used to reply to data requests using DataReply objects. */
    public static final String DATA_REPLY_CHANNEL = "org.gni.data.reply";

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

        //Create eventmanager and start microservices
        EventManager eventManager = QBit.factory().systemEventManager();
        UserService userService = new UserService();
        Ledger ledger = new Ledger();
        UIService uiService = new UIService();
        TransactionDispatchService dispatchService
                                            = new TransactionDispatchService();
        TransactionReceiveService receiveService
                                            = new TransactionReceiveService();

        //Set listener on microservices
        eventManager.listen(userService);
        eventManager.listen(ledger);
        eventManager.listen(uiService);
        eventManager.listen(dispatchService);
        eventManager.listen(receiveService);

        //Emulate user using the uiService
        //TODO move Service calls to the services themselves.
        // Does not work at the moment because there is no code to call the
        // methods.
        System.out.println("Manager: Creating customer");
        uiService.createCustomer("freek", "de wilde",
                                "NL52INGB0987890998");
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
        Transaction transaction = new Transaction(112,
                                        testAccountNumber,
                                        testDestinationNumber,
                                        "de wilde",
                                        250);
        eventManager.send(TRANSACTION_REQUEST_CHANNEL, transaction);
        sleep(200);

        System.out.println("Manager: Creating transaction");
        Transaction transaction2 = new Transaction(113,
                                        testAccountNumber,
                                        testDestinationNumber,
                                        "de wilde",
                                        250);
        eventManager.send(TRANSACTION_REQUEST_CHANNEL, transaction2);
        sleep(200);

        System.out.println("Manager: Requesting customer balance");
        uiService.requestBalance(testAccountNumber);
        sleep(200);

        //Test method.
        System.out.println("Manager: Printing ledger:");
        ledger.printLedger();
    }
}
