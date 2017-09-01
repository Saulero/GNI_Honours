package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.MessageWrapper;
import databeans.MethodType;
import databeans.Transaction;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.ApiService.MAX_ACCOUNT_NUMBER_LENGTH;
import static api.ApiService.DESCRIPTION_LIMIT;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static api.methods.SharedUtilityMethods.valueHasCorrectLength;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class TransferMoney {

    /**
     * Transfer money between accounts, the authToken needs to belong to a user that is authorized to make transactions
     * from the sourceAccount.
     * @param params Parameters of the request (authToken, sourceIBAN, targetIBAN, targetName, amount, description).
     * @param api DataBean containing everything in the ApiService
     */
    public static void transferMoney(final Map<String, Object> params, final ApiBean api) {
        String cookie = (String) params.get("authToken");
        Transaction transaction = JSONParser.createJsonTransaction(-1, (String) params.get("sourceIBAN"),
                (String) params.get("targetIBAN"), (String) params.get("targetName"),
                (String) params.get("description"), (Double) params.get("amount"), false, false);
        System.out.printf("%s Sending internal transaction.\n", PREFIX);
        handleTransactionExceptions(transaction, cookie, api);
    }

    /**
     * Tries to verify the input of a transaction request and then forward the request, sends an error if an
     * exception occurs.
     * @param transaction The transaction to test/process
     * @param cookie Cookie of the user that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleTransactionExceptions(
            final Transaction transaction, final String cookie, final ApiBean api) {
        try {
            verifyTransactionInput(transaction);
            doTransactionRequest(transaction, cookie, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s, sending rejection.\n", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                    "Unknown error occurred.", "Syntax error when parsing json."), api);
        } catch (NumberFormatException e) {
            System.out.printf("%s The transaction amount was incorrectly specified, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.",
                    "The following variable was incorrectly specified: transactionAmount."), api);
        }
    }

    /**
     * Checks if the input for a transaction request is correctly formatted and contains correct values.
     * @param transaction The transaction request.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     * @throws NumberFormatException Thrown when a string value could not be parsed to a Long.
     */
    private static void verifyTransactionInput(final Transaction transaction)
            throws IncorrectInputException, JsonSyntaxException, NumberFormatException {
        final String sourceAccountNumber = transaction.getSourceAccountNumber();
        final String destinationAccountNumber = transaction.getDestinationAccountNumber();
        final String destinationAccountHolderName = transaction.getDestinationAccountHolderName();
        final String transactionDescription = transaction.getDescription();
        final double transactionAmount = transaction.getTransactionAmount();
        if (sourceAccountNumber == null || sourceAccountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: sourceAccountNumber.");
        } else if (destinationAccountNumber == null || destinationAccountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified:"
                    + " destinationAccountNumber.");
        } else if (destinationAccountHolderName == null || !valueHasCorrectLength(destinationAccountHolderName)) {
            throw new IncorrectInputException("The following variable was incorrectly specified:"
                    + " destinationAccountHolderName.");
        } else if (transactionDescription == null || transactionDescription.length() > DESCRIPTION_LIMIT) {
            throw new IncorrectInputException("The following variable was incorrectly specified:"
                    + " transactionDescription.");
        } else if (transactionAmount < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: transactionAmount.");
        } else if (transaction.isProcessed()) {
            throw new IncorrectInputException("The following variable was incorrectly specified: isProcessed.");
        } else if (transaction.isSuccessful()) {
            throw new IncorrectInputException("The following variable was incorrectly specified: isSuccessful.");
        }
    }

    /**
     * Forwards transaction request to the Authentication service and forwards the reply or sends a rejection if the
     * request fails.
     * @param transaction Transaction request that should be processed.
     * @param cookie Cookie of the User that sent the request.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doTransactionRequest(final Transaction transaction, final String cookie, final ApiBean api) {
        MessageWrapper data = new MessageWrapper();
        data.setCookie(cookie);
        data.setMethodType(MethodType.TRANSFER_MONEY);
        data.setData(api.getJsonConverter().toJson(transaction));

        System.out.printf("%s Forwarding transaction request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/transaction",
                "data", api.getJsonConverter().toJson(data),
                (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendTransactionCallback((Transaction) messageWrapper.getData(), api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        sendErrorReply(JSONParser.createMessageWrapper(true, 500,
                                "An unknown error occurred.",
                                "There was a problem with one of the HTTP requests"), api);
                    }
                });
    }

    /**
     * Forwards the result of a transaction request to the service that sent the request.
     * @param reply The processed transaction
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendTransactionCallback(final Transaction reply, final ApiBean api) {
        if (reply.isSuccessful() && reply.isProcessed()) {
            long transactionId = reply.getTransactionID();
            System.out.printf("%s Internal transaction %d successful.\n\n\n\n", PREFIX, transactionId);
            Map<String, Object> result = new HashMap<>();
            JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
            api.getCallbackBuilder().build().reply(response.toJSONString());
        } else {
            System.out.printf("%s Internal transaction was not successful\n\n\n\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 500, "Unknown error occurred."), api);
        }
    }
}
