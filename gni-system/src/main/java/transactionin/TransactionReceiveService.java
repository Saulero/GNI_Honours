package transactionin;

//import io.advantageous.qbit.annotation.Listen;
import util.Transaction;
//import manager.ServiceManager;

/**
 * @author Noel
 * @version 1
 * Receives transaction requests from external banks, send them to the ledger
 * for processing, and sends the confirmation/failure back to the external bank.
 */
public class TransactionReceiveService {
    //TODO setup socket to receive external traffic and parse it.

    /**
     * Receives transaction requests from external banks and sends them to the
     * ledger for processing.
     */
    //TODO rewrite for external json requests.
    private void processIncomingTransaction() {
        //TODO code to process transactions from other banks.
        System.out.println("Processing transaction from an external bank.");
    }

    /**
     * Receives a transaction object back from the ledger then checks if it was
     * successfull or failed and sends this back to the bank of origin.
     * @param transaction Transaction that was processed by the ledger.
     */
    //@Listen(ServiceManager.TRANSACTION_VERIFICATION_CHANNEL)
    private void sendIncomingTransactionReply(final Transaction transaction) {
        //TODO code to check whether a transaction was internal or external.
        System.out.printf("Sent transaction reply to transaction number %s",
                        transaction.getTransactionID());
    }
}
