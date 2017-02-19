package ui;

import io.advantageous.qbit.annotation.Listen;
import ledger.Transaction;
import queue.ServiceManager;
import users.Customer;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 5-2-17.
 * Interface that outside users can use to view their balance, transaction
 * history, and make transactions.
 */
public final class UIService {

    /**
     * Send a transaction request to the ledger service that will reply with
     * the transaction history of the account.
     * @param accountNumber Account number to request the transaction history
     *                      for.
     */
    public void requestTransactionHistory(final String accountNumber) {
        DataRequest request = new DataRequest(accountNumber,
                                            RequestType.TRANSACTIONHISTORY);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    /**
     * Handles replies coming back from other services.
     * Checks what kind of reply it is and handles it accordingly.
     * @param dataReply Reply object containing the type of request and its
     *                  reply data
     */
    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    private void processReply(final DataReply dataReply) {
        if (dataReply.getType() == RequestType.TRANSACTIONHISTORY) {
            System.out.printf("UI: Your transaction history: %s\n\n", dataReply.getTransactions());
        } else if (dataReply.getType() == RequestType.BALANCE) {
            System.out.printf("UI: Your balance: %s\n\n", dataReply.getAccountData().getBalance());
        } else if (dataReply.getType() == RequestType.CUSTOMERDATA) {
            System.out.printf("UI: Your customer information: %s\n\n", dataReply.getAccountData());
        }
    }

    /**
     * Sends a balance request to the ledger service for an account.
     * @param accountNumber Account number to request the balance for
     */
    public void requestBalance(final String accountNumber) {
        DataRequest request = new DataRequest(accountNumber,
                                            RequestType.BALANCE);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }


    /**
     * Sends a customer data request to the user service for an account.
     * @param accountNumber Account number to request the balance for
     */
    //TODO needs to be reworked to not be dependent on accountNumber
    public void requestCustomerData(final String accountNumber) {
        DataRequest request = new DataRequest(accountNumber,
                                            RequestType.CUSTOMERDATA);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    /**
     * Send a transaction request to the transaction out service.
     * @param sourceAccountNumber Account number to draw the funds from
     * @param amount Amount of money to send
     * @param destinationAccountNumber Account Number to send the money to
     * @param destinationAccountHolderName The name of the owner of the
     *                                     destination account number
     * @param transactionNumber Transaction number used for processing
     */
    //TODO generate transaction number in ledger, process reply
    public void doTransaction(final String sourceAccountNumber,
                              final double amount,
                              final String destinationAccountNumber,
                              final String destinationAccountHolderName,
                              final long transactionNumber) {
        //Do transaction work
        System.out.printf("UI: Executed new transaction\n\n");

        Transaction transaction = new Transaction(transactionNumber,
                                                sourceAccountNumber,
                                                destinationAccountNumber,
                                                destinationAccountHolderName,
                                                amount);

        serviceContext().send(ServiceManager.TRANSACTION_REQUEST_CHANNEL,
                            transaction);
    }

    /**
     * Send a user creation request over the USER_CREATION_CHANNEL to create
     * a new user in the system.
     * @param name First name of the user to create
     * @param surname Surname of the user to create
     * @param accountNumber Account number of the user to create
     */
    //TODO Account number needs to be requested from the ledger
    public void createCustomer(final String name, final String surname,
                               final String accountNumber) {
        //TODO move function from userService to this service.
        System.out.printf("UI: Creating customer with name: %s %s\n\n", name,
                        surname);
        Customer customer = new Customer(name, surname, accountNumber);
        serviceContext().send(ServiceManager.USER_CREATION_CHANNEL, customer);
    }
}


