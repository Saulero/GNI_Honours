package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.*;
import util.JSONParser;

import java.util.*;

import static api.ApiService.PREFIX;
import static api.ApiService.accountNumberLength;
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
     */
    public static void handleDataRequestExceptions(
            final DataRequest dataRequest, final String cookie, final long nrOfTransactions, final ApiBean api) {
        try {
            verifyDataRequestInput(dataRequest);
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
        } else if (accountNumber == null && isAccountNumberRelated(dataRequest.getType())) {
            throw new IncorrectInputException("AccountNumber specified is null.");
        } else if (accountNumber != null && accountNumber.length() != accountNumberLength && isAccountNumberRelated(dataRequest.getType())) {
            throw new IncorrectInputException("AccountNumber specified is of an incorrect length.");
        }
    }

    /**
     * Returns a boolean indicating if the request type is related to a specific accountNumber.
     * @param requestType Type of request to check.
     * @return Boolean indicating if the requestType relates to an accountNumber.
     */
    private static boolean isAccountNumberRelated(final RequestType requestType) {
        return requestType != RequestType.CUSTOMERDATA && requestType != RequestType.CUSTOMERACCESSLIST;
    }

    /**
     * Forwards the data request to the Authentication service and sends the reply off to processing,
     * or rejects the request if the forward fails.
     * @param dataRequest A dataRequest that should be sent to the Authentication Service.
     */
    private static void doDataRequest(final DataRequest dataRequest, final String cookie, final long nrOfTransactions, final ApiBean api) {
        System.out.printf("%s Forwarding data request.\n", PREFIX);
        api.getAuthenticationClient().getAsyncWith2Params("/services/authentication/data",
                "request", api.getJsonConverter().toJson(dataRequest), "cookie", cookie,
                (httpStatusCode, httpContentType, dataReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(dataReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            processDataReply((DataReply) messageWrapper.getData(), dataRequest, nrOfTransactions, api);
                        } else {
                            sendErrorReply(messageWrapper, api);
                        }
                    } else {
                        sendErrorReply(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests"), api);
                    }
                });
    }

    /**
     * Checks if a data request was successful and sends the reply back to the source of the request.
     * @param dataReply {@link DataReply}
     * @param dataRequest The {@link DataRequest} that was forwarded.
     */
    private static void processDataReply(
            final DataReply dataReply, final DataRequest dataRequest, final long nrOfTransactions, final ApiBean api) {
        RequestType requestType = dataRequest.getType();
        switch (requestType) {
            case BALANCE:
                System.out.printf("%s Request successful, balance: %f\n\n\n\n",
                        PREFIX, dataReply.getAccountData().getBalance());
                Map<String, Object> balance = new HashMap<>();
                balance.put("balance", dataReply.getAccountData().getBalance());
                sendDataRequestResponse(balance, api);
                break;
            case TRANSACTIONHISTORY:
                System.out.printf("%s TransactionOverview request successful.\n\n\n\n", PREFIX);
                List<Map<String, Object>> transactionList = new ArrayList<>();
                List<Transaction> transactions = dataReply.getTransactions().subList(0,
                        Math.toIntExact(nrOfTransactions) - 1);
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
            case CUSTOMERDATA:
                System.out.printf("%s Sending customer data request callback.\n", PREFIX);
                api.getCallbackBuilder().build().reply(dataReply);
                break;
            case CUSTOMERACCESSLIST:
                System.out.printf("%s Sending customer access list request callback.\n", PREFIX);
                api.getCallbackBuilder().build().reply(dataReply);
                break;
            case ACCOUNTACCESSLIST:
                System.out.printf("%s Sending account access list request callback.\n", PREFIX);
                api.getCallbackBuilder().build().reply(dataReply);
                break;
            default:
                sendErrorReply(JSONParser.createMessageWrapper(true, 500, "Internal system error occurred.", "Incorrect requestType specified."), api);
                break;
        }
    }

    private static void sendDataRequestResponse(final Object result, final ApiBean api) {
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
