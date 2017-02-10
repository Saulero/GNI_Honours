import io.advantageous.qbit.annotation.Listen;

/**
 * Created by noel on 5-2-17.
 *
 */
class TransactionReceiveService {
    //TODO setup socket to receive external traffic and parse it.

    //TODO rewrite for external json requests.
    void process_incoming_transaction() {
        //TODO code to process transactions from other banks.
        System.out.println("Processing transaction from an external bank.");
    }

    //@Listen(ServiceManager.TRANSACTION_PROCESSING_CHANNEL)
    void send_incoming_transaction_reply(final Transaction transaction) {
        //TODO code to check whether a transaction was internal or external.
        System.out.printf("Sent transaction reply to transaction number %s", transaction.getTransactionNumber());
    }
}
