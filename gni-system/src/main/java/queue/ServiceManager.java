package queue;

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
     * incoming Transaction objects to the ledger. */
    public static final String INCOMING_TRANSACTION_CHANNEL = "org.gni.transaction.incoming";

    /** Channel used by transaction dispatch services to send
     * outgoing Transaction objects to the ledger. */
    public static final String OUTGOING_TRANSACTION_CHANNEL = "org.gni.transaction.outgoing";

    /** Channel used by the ledger to send processed incoming Transaction
     * objects back to the dispatch services. */
    public static final String INCOMING_TRANSACTION_VERIFICATION_CHANNEL = "org.gni.transaction.verify.incoming";

    /** Channel used by the ledger to send processed outgoing Transaction
     * objects back to the dispatch services. */
    public static final String OUTGOING_TRANSACTION_VERIFICATION_CHANNEL = "org.gni.transaction.verify.outgoing";

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
/*
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
*/
    }
}
