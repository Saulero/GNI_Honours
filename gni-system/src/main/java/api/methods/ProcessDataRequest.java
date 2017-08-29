package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.DataReply;
import databeans.DataRequest;
import databeans.MessageWrapper;
import databeans.RequestType;
import databeans.Transaction;
import util.JSONParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.ApiService.MAX_ACCOUNT_NUMBER_LENGTH;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class ProcessDataRequest {

    /**
     * Handles the exceptions that occur when verifying the input of the data request, and sends a rejection
     * if the input for the request is incorrect.
     * @param dataRequest The data request that was received.
     * @param cookie Cookie of the user that sent the data request.
     * @param nrOfTransactions The number of transactions (in case of a transactionHistoryRequest)
     * @param api DataBean containing everything in the ApiService
     */
    public static void handleDataRequestExceptions(
            final MessageWrapper dataRequest, final String cookie, final long nrOfTransactions, final ApiBean api) {
        try {
            verifyDataRequestInput((DataRequest) dataRequest.getData());
            doDataRequest(dataRequest, cookie, nrOfTransactions, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s, sending rejection.\n", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(
                    true, 418, "One of the parameters has an invalid value.", e.getMessage()), api);
        } catch (JsonSyntaxException e) {
            System.out.printf("%s Incorrect json syntax detected, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(
                    true, 500, "Unknown error occurred.", "Incorrect json syntax used."), api);
        }
    }

    /**
     * Checks if the input for the data request is acceptable.
     * @param dataRequest The data request that was received.
     * @throws IncorrectInputException Thrown when a field does not contain an acceptable value.
     * @throws JsonSyntaxException Thrown when the Json submitted for the data request is not correct(can't be parsed).
     */
    private static void verifyDataRequestInput(final DataRequest dataRequest)
            throws IncorrectInputException, JsonSyntaxException {
        RequestType requestType = dataRequest.getType();
        String accountNumber = dataRequest.getAccountNumber();

        if (requestType == null || !Arrays.asList(RequestType.values()).contains(dataRequest.getType())) {
            throw new IncorrectInputException("RequestType not correctly specified.");
        } else if (accountNumber == null && dataRequest.getType() != RequestType.CUSTOMERACCESSLIST) {
            throw new IncorrectInputException("AccountNumber specified is null.");
        } else if (accountNumber != null && accountNumber.length()
                > MAX_ACCOUNT_NUMBER_LENGTH && dataRequest.getType() != RequestType.CUSTOMERACCESSLIST) {
            throw new IncorrectInputException("AccountNumber specified is of an incorrect length.");
        }
    }

    /**
     * Forwards the data request to the Authentication service and sends the reply off to processing,
     * or rejects the request if the forward fails.
     * @param dataRequest A dataRequest that should be sent to the Authentication Service.
     * @param cookie Cookie of the user that sent the data request.
     * @param nrOfTransactions The number of transactions (in case of a transactionHistoryRequest)
     * @param api DataBean containing everything in the ApiService
     */
    private static void doDataRequest(
            final MessageWrapper dataRequest, final String cookie, final long nrOfTransactions, final ApiBean api) {
        System.out.printf("%s Forwarding data request.\n", PREFIX);
        api.getAuthenticationClient().getAsyncWith2Params("/services/authentication/data",
                "request", api.getJsonConverter().toJson(dataRequest), "cookie", cookie,
                (httpStatusCode, httpContentType, dataReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            processDataReply((DataReply) messageWrapper.getData(), dataRequest, nrOfTransactions, api);
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
     * Checks if a data request was successful and sends the reply back to the source of the request.
     * @param dataReply {@link DataReply}
     * @param dataRequest The {@link DataRequest} that was forwarded.
     * @param nrOfTransactions The number of transactions (in case of a transactionHistoryRequest)
     * @param api DataBean containing everything in the ApiService
     */
    private static void processDataReply(
            final DataReply dataReply, final MessageWrapper dataRequest,
            final long nrOfTransactions, final ApiBean api) {
        RequestType requestType = ((DataRequest) dataRequest.getData()).getType();
        switch (requestType) {
            case BALANCE:
                Double balance = dataReply.getAccountData().getBalance();
                Double savingBalance = dataReply.getAccountData().getSavingsBalance();
                System.out.printf("%s Request successful, balance: %f\n\n\n\n",
                        PREFIX, dataReply.getAccountData().getBalance());
                Map<String, Object> balanceResult = new HashMap<>();
                balanceResult.put("balance", balance.toString());
                balanceResult.put("savingAccountBalance", savingBalance);
                Double creditCardBalance = dataReply.getAccountData().getCreditCardBalance();
                if (creditCardBalance != null) {
                    balanceResult.put("creditCardBalance", creditCardBalance);
                }
                sendDataRequestResponse(balanceResult, api);
                break;
            case TRANSACTIONHISTORY:
                System.out.printf("%s TransactionOverview request successful.\n\n\n\n", PREFIX);
                List<Map<String, Object>> transactionList = new ArrayList<>();
                int amountOfTransactions = Math.toIntExact(nrOfTransactions);
                List<Transaction> transactions = dataReply.getTransactions();
                if (amountOfTransactions < transactions.size()) {
                    transactions = transactions.subList(0, amountOfTransactions);
                }
                for (Transaction transaction : transactions) {
                    Map<String, Object> transactionMap = new HashMap<>();
                    transactionMap.put("sourceIBAN", transaction.getSourceAccountNumber());
                    transactionMap.put("targetIBAN", transaction.getDestinationAccountNumber());
                    transactionMap.put("targetName", transaction.getDestinationAccountHolderName());
                    transactionMap.put("date", transaction.getDate().toString());
                    transactionMap.put("amount", transaction.getTransactionAmount());
                    transactionMap.put("description", transaction.getDescription());
                    transactionList.add(transactionMap);
                }
                sendDataRequestResponse(transactionList, api);
                break;
            case CUSTOMERACCESSLIST:
                System.out.printf("%s Accounts request successful.\n\n\n\n", PREFIX);
                List<Map<String, Object>> accounts = new ArrayList<>();
                dataReply.getAccounts().forEach(k -> {
                    Map<String, Object> account = new HashMap<>();
                    account.put("iBAN", k.getAccountNumber());
                    account.put("owner", k.getUsername());
                    accounts.add(account);
                });
                sendDataRequestResponse(accounts, api);
                break;
            case ACCOUNTACCESSLIST:
                System.out.printf("%s BankAccountAccess request successful.\n\n\n\n", PREFIX);
                List<Map<String, Object>> accountList = new ArrayList<>();
                dataReply.getAccounts().forEach(k -> {
                    Map<String, Object> account = new HashMap<>();
                    account.put("username", k.getUsername());
                    accountList.add(account);
                });
                sendDataRequestResponse(accountList, api);
                break;
            case OVERDRAFTLIMIT:
                Double overdraftLimit = dataReply.getAccountData().getOverdraftLimit();
                System.out.printf(
                        "%s Successfully queried the current overdraft limit: %f.\n\n\n\n", PREFIX, overdraftLimit);
                Map<String, Object> result = new HashMap<>();
                result.put("overdraftLimit", overdraftLimit);
                sendDataRequestResponse(result, api);
                break;
            default:
                sendErrorReply(JSONParser.createMessageWrapper(
                        true, 500, "Internal system error occurred.", "Incorrect requestType specified."), api);
                break;
        }
    }

    /**
     * Takes an object an constructs a protocol complient response to send back the the original source.
     * @param result The Object to send.
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendDataRequestResponse(final Object result, final ApiBean api) {
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
