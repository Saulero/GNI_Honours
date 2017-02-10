import io.advantageous.qbit.annotation.Listen;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 5-2-17.
 * Interface that outside users can use to view their balance, transaction history, and make transactions.
 */
class UIService {

    void requestTransactionHistory(String accountNumber) {
        DataRequest request = new DataRequest(accountNumber, DataRequest.requestType.TRANSACTIONHISTORY);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    void processTransactionHistoryReply(final DataReply dataReply) {
        if (dataReply.getType() == DataRequest.requestType.TRANSACTIONHISTORY) {
            System.out.printf("UI: Your transaction history: %s\n\n", dataReply.getData());
        }
    }

    void requestBalance(String AccountNumber) {
        DataRequest request = new DataRequest(AccountNumber, DataRequest.requestType.BALANCE);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    void processBalanceReply(final DataReply dataReply) {
        if (dataReply.getType() == DataRequest.requestType.BALANCE) {
            System.out.printf("UI: Your balance: %s\n\n", dataReply.getData());
        }
    }

    void requestCustomerData(String AccountNumber) {
        DataRequest request = new DataRequest(AccountNumber, DataRequest.requestType.CUSTOMERDATA);
        serviceContext().send(ServiceManager.DATA_REQUEST_CHANNEL, request);
    }

    @Listen(ServiceManager.DATA_REPLY_CHANNEL)
    void processCustomerData(final DataReply dataReply) {
        if (dataReply.getType() == DataRequest.requestType.CUSTOMERDATA) {
            System.out.printf("UI: Your customer information: %s\n\n", dataReply.getData());
        }
    }

    //TODO generate transaction number
    void doTransaction(String sourceAccountNumber, double amount, String destinationAccountNumber,
                       String destinationAccountHolderName, String transactionNumber) {
        //Do transaction work
        System.out.printf("UI: Executed new transaction\n\n");

        Transaction transaction = new Transaction(sourceAccountNumber, amount, destinationAccountNumber,
                destinationAccountHolderName, transactionNumber);

        serviceContext().send(ServiceManager.TRANSACTION_REQUEST_CHANNEL, transaction);
    }

    void createCustomer(String name, String surname, String accountNumber) {
        //TODO move function from userService to this service.
        System.out.printf("UI: Creating customer with name: %s %s\n\n", name, surname);
        Customer customer = new Customer(name, surname, accountNumber);
        serviceContext().send(ServiceManager.USER_CREATION_CHANNEL, customer);
    }
}

class DataRequest {
    enum requestType {
        TRANSACTIONHISTORY, BALANCE, CUSTOMERDATA
    }
    private String accountNumber;
    private requestType type;

    DataRequest(String accountNumber, requestType type) {
        this.accountNumber = accountNumber;
        this.type = type;
    }

    String getAccountNumber() {
        return this.accountNumber;
    }

    requestType getType() {
        return this.type;
    }
}

class DataReply {
    private String accountNumber;
    private DataRequest.requestType type;
    private String data;

    DataReply(String accountNumber, DataRequest.requestType type, String data) {
        this.accountNumber = accountNumber;
        this.type = type;
        this.data = data;
    }

    String getAccountNumber() {
        return accountNumber;
    }

    DataRequest.requestType getType() {
        return type;
    }

    String getData() {
        return data;
    }
}
