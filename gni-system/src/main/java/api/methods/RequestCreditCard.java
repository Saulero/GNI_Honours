package api.methods;

import api.ApiBean;
import api.ApiService;
import api.IncorrectInputException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.CreditCard;
import databeans.MessageWrapper;
import databeans.MethodType;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Noel
 */
public abstract class RequestCreditCard {

    public static void requestCreditCard(final Map<String, Object> params, final ApiBean api) {
        String cookie = (String) params.get("authToken");
        String accountNumber = (String) params.get("iBAN");
        handleNewCreditCardExceptions(cookie, accountNumber, api);
    }

    private static void handleNewCreditCardExceptions(final String cookie, final String accountNumber, final ApiBean api) {
        try {
            verifyNewCreditCardInput(cookie, accountNumber);
            doNewCreditCardRequest(cookie, accountNumber, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s One of the parameters has an invalid value, sending error.", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        }
    }

    private static void verifyNewCreditCardInput(String cookie, final String accountNumber) throws IncorrectInputException {
        if (accountNumber.length() > ApiService.MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new IncorrectInputException("The following variable was incorrectly specified: iBAN.");
        } else if (cookie == null) {
            throw new IncorrectInputException("The following variable was incorrectly specified: authToken.");
        }
    }

    private static void doNewCreditCardRequest(final String cookie, final String accountNumber, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.REQUEST_CREDIT_CARD);
        data.setData(accountNumber);

        System.out.printf("%s Forwarding new credit card request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/creditCard",
                "data", api.getJsonConverter().toJson(data), (httpStatusCode, httpContentType, replyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(replyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            CreditCard creditCard = (CreditCard) messageWrapper.getData();
                            sendRequestCreditCardCallback(creditCard, api);
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

    private static void sendRequestCreditCardCallback(final CreditCard creditCard, final ApiBean api) {
        System.out.printf("%s Successfully created credit card, creating pin card for this credit card.\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        result.put("pinCard", creditCard.getCreditCardNumber());
        result.put("pinCode", creditCard.getPinCode());
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
