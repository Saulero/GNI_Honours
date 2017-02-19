package util;

/**
 * Created by noel on 18-2-17.
 * @author noel
 * @version 1
 */
public class Util {

    public static DataReply createJsonReply(String accountNumber, RequestType type, String data) {
        DataReply reply = new DataReply();
        reply.setAccountNumber(accountNumber);
        reply.setType(type);
        reply.setData(data);
        return reply;
    }

    public static DataRequest createJsonRequest(String accountNumber, RequestType type) {
        DataRequest request = new DataRequest();
        request.setType(type);
        request.setAccountNumber(accountNumber);
        return request;
    }

    public static Transaction createJsonTransaction(long transactionID, String sourceAccountNumber,
                                                    String destinationAccountNumber,
                                                    String destinationAccountHolderName, double transactionAmount,
                                                    boolean processed, boolean successfull) {
        Transaction transaction = new Transaction();
        transaction.setTransactionID(transactionID);
        transaction.setSourceAccountNumber(sourceAccountNumber);
        transaction.setDestinationAccountNumber(destinationAccountNumber);
        transaction.setDestinationAccountHolderName(destinationAccountHolderName);
        transaction.setTransactionAmount(transactionAmount);
        transaction.setProcessed(processed);
        transaction.setSuccessfull(successfull);
        return transaction;
    }
}
