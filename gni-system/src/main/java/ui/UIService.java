package ui;

import io.advantageous.qbit.annotation.Listen;
import ledger.Transaction;
import queue.ServiceManager;
import users.Customer;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 5-2-17.
 * Interface that outside users can use to view their balance, transaction history, and make transactions.
 */
public class UIService {

    public void requestTransactionHistory(String accountNumber) {
        DataRequest request = new DataRequest(accountNumber, RequestType.TRANSACTIONHISTORY);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    public void processTransactionHistoryReply(final DataReply dataReply) {
        if (dataReply.getType() == RequestType.TRANSACTIONHISTORY) {
            System.out.printf("UI: Your transaction history: %s\n\n", dataReply.getData());
        }
    }

    public void requestBalance(String AccountNumber) {
        DataRequest request = new DataRequest(AccountNumber, RequestType.BALANCE);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    public void processBalanceReply(final DataReply dataReply) {
        if (dataReply.getType() == RequestType.BALANCE) {
            System.out.printf("UI: Your balance: %s\n\n", dataReply.getData());
        }
    }

    public void requestCustomerData(String AccountNumber) {
        DataRequest request = new DataRequest(AccountNumber, RequestType.CUSTOMERDATA);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    public void processCustomerData(final DataReply dataReply) {
        if (dataReply.getType() == RequestType.CUSTOMERDATA) {
            System.out.printf("UI: Your customer information: %s\n\n", dataReply.getData());
        }
    }

    //TODO generate transaction number
    public void doTransaction(String sourceAccountNumber, double amount, String destinationAccountNumber,
                       String destinationAccountHolderName, long transactionNumber) {
        //Do transaction work
        System.out.printf("UI: Executed new transaction\n\n");

        Transaction transaction = new Transaction(transactionNumber, sourceAccountNumber, destinationAccountNumber, destinationAccountHolderName, amount);

        serviceContext().send(ServiceManager.TRANSACTION_REQUEST_CHANNEL, transaction);
    }

    public void createCustomer(String name, String surname, String accountNumber) {
        //TODO move function from userService to this service.
        System.out.printf("UI: Creating customer with name: %s %s\n\n", name, surname);
        Customer customer = new Customer(name, surname, accountNumber);
        serviceContext().send(ServiceManager.USER_CREATION_CHANNEL, customer);
    }
}


