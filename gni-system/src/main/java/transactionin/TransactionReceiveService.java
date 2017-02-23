package transactionin;

import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;

/**
 * @author Noel
 * @version 1
 * Receives transaction requests from external banks, send them to the ledger
 * for processing, and sends the confirmation/failure back to the external bank.
 */
@RequestMapping("/transactionReceive")
public class TransactionReceiveService {
    /**Used to verify if a http request to another service was successfull.*/
    private static final int HTTP_OK = 200;
    /**Port that the Ledger service can be found on.*/
    private int ledgerPort;
    /**Host that the User service can be found on.*/
    private String ledgerHost;

    /**
     * Constructor.
     * @param newLedgerPort Port the Ledger can be found on.
     * @param newLedgerHost Host the ledger can be found on.
     */
    public TransactionReceiveService(final int newLedgerPort, final String newLedgerHost) {
        this.ledgerPort = newLedgerPort;
        this.ledgerHost = newLedgerHost;
    }

    /**
     * Processes method coming from other banks, sends them to the ledger and then sends back a reply.
     * @param callback Used to send a reply back to the external bank.
     * @param body Json String representing an external transaction.
     */
    //TODO might need reworking when it is clear how external transactions will be sent
    public void processIncomingTransaction(final Callback<String> callback, final @RequestParam("body") String body) {
        //TODO fill method

        System.out.println("Processing transaction from an external bank.");
    }
}
