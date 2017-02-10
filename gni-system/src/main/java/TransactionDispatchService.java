import io.advantageous.qbit.annotation.Listen;
import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 5-2-17.
 * Receives outgoing transaction requests.
 * Sends these requests to the ledger for processing.
 * Handles the response from the ledger and sends the transaction to its respective receiving bank.
 */
class TransactionDispatchService {
    //TODO setup socket to receive external traffic and parse it.

    /**
     * Receives transaction requests from TRANSACTION_REQUEST_CHANNEL
     * Dispatches these requests to the ledger through TRANSACTION_PROCESSING_CHANNEL
     * @param transaction the transaction to process
     */
    //TODO rewrite for outgoing json requests.
    @Listen(value = ServiceManager.TRANSACTION_REQUEST_CHANNEL, consume = true)
    void process_transaction_request(final Transaction transaction) {
        //Send request to ledger to check if customer has correct balance.
        System.out.printf("Dispatch: Processing transaction request number %s\n", transaction.getTransactionNumber());
        serviceContext().send(ServiceManager.TRANSACTION_PROCESSING_CHANNEL, transaction);
    }

    /**
     * Receives transactions back from the ledger through TRANSACTION_VERIFICATION_CHANNEL
     * If the transaction is successfull generates json packet and sends it to the bank of the destination account
     * If the transaction fails processes the transaction failure accordingly
     * @param transaction the transaction reply from the ledger
     */
    @Listen(ServiceManager.TRANSACTION_VERIFICATION_CHANNEL)
    void execute_transaction(final Transaction transaction) {
        if (transaction.getProcessed()) {
            if(transaction.getSuccessfull()) {
                //TODO generate outgoing json and send it.
                System.out.printf("Dispatch: Sent transaction %s to respective bank.\n",
                        transaction.getTransactionNumber());
            } else {
                //TODO code to process payment failure
                System.out.printf("Dispatch: Transaction %s failed.\n\n", transaction.getTransactionNumber());
            }
        }
    }
}
