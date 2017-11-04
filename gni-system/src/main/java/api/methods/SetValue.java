package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.*;
import util.JSONParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import static api.ApiService.*;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static api.methods.SharedUtilityMethods.valueHasCorrectLength;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public abstract class SetValue {

    public static void setValue(final Map<String, Object> params, final ApiBean api) {
        String cookie = (String) params.get("authToken");
        String key = (String) params.get("key");
        BigDecimal valueParam = (BigDecimal) params.get("value");
        double value = valueParam.doubleValue();
        String date = (String) params.get("date");

        System.out.printf("%s Checking parameters...\n", PREFIX);
        handleSetValueExceptions(key, value, date, cookie, api);
    }

    private static void handleSetValueExceptions(
            final String key, final double value, final String date, final String cookie, final ApiBean api) {
        try {
            SetValueKey setValueKey = SetValueKey.getValue(key);
            LocalDate localDate = LocalDate.parse(date);
            checkNumberFormat(value, setValueKey);
            doSetValueRequest(new SetValueRequest(setValueKey, value, localDate), cookie, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s, sending rejection.\n", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", e.getMessage()), api);
        } catch (DateTimeParseException e) {
            System.out.printf("%s The provided date does not meet the correct format, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.",
                    "The provided date does not meet the correct format."), api);
        } catch (NumberFormatException e) {
            System.out.printf("%s The transaction amount was incorrectly specified, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", e.getMessage()), api);
        }
    }

    private static void checkNumberFormat(final double value, final SetValueKey key) throws NumberFormatException {
        boolean isCorrect = false;
        switch (key) {
            case CREDIT_CARD_MONTHLY_FEE:       isCorrect = isMoneyFormat(value);
                break;
            case CREDIT_CARD_DEFAULT_CREDIT:    isCorrect = isMoneyFormat(value);
                break;
            case CARD_EXPIRATION_LENGTH:        isCorrect = isIntegerFormat(value);
                break;
            case NEW_CARD_COST:                 isCorrect = isMoneyFormat(value);
                break;
            case CARD_USAGE_ATTEMPTS:           isCorrect = isIntegerFormat(value);
                break;
            case MAX_OVERDRAFT_LIMIT:           isCorrect = isMoneyFormat(value);
                break;
            case INTEREST_RATE_1:               isCorrect = isPercentageFormat(value);
                break;
            case INTEREST_RATE_2:               isCorrect = isPercentageFormat(value);
                break;
            case INTEREST_RATE_3:               isCorrect = isPercentageFormat(value);
                break;
            case OVERDRAFT_INTEREST_RATE:       isCorrect = isPercentageFormat(value);
                break;
            case DAILY_WITHDRAW_LIMIT:          isCorrect = isMoneyFormat(value);
                break;
            case WEEKLY_TRANSFER_LIMIT:         isCorrect = isMoneyFormat(value);
                break;
        }
        if (!isCorrect) {
            throw new NumberFormatException("The value does not match the required format for the provided key.");
        }
    }

    private static boolean isMoneyFormat(final double value) {
        String text = Double.toString(Math.abs(value));
        int skip = text.indexOf('.');
        return (text.length() - skip - 1) <= 2;
    }

    private static boolean isIntegerFormat(final double value) {
        return value == Math.floor(value);
    }

    private static boolean isPercentageFormat(final double value) {
        return value >= 0 && value <= 1;
    }

    private static void doSetValueRequest(final SetValueRequest request, final String cookie, final ApiBean api) {
        MessageWrapper data = JSONParser.createMessageWrapper(false, 0, "Request");
        data.setCookie(cookie);
        data.setMethodType(MethodType.SET_VALUE);
        data.setData(request);

        System.out.printf("%s Forwarding setValue request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/setValue",
                "data", api.getJsonConverter().toJson(data),
                (httpStatusCode, httpContentType, transactionReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(transactionReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendSetValueCallback(api);
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

    private static void sendSetValueCallback(final ApiBean api) {
        System.out.printf("%s SetValue request successful.\n\n\n\n", PREFIX);
        Map<String, Object> result = new HashMap<>();
        JSONRPC2Response response = new JSONRPC2Response(result, api.getId());
        api.getCallbackBuilder().build().reply(response.toJSONString());
    }
}
