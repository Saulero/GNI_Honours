package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import databeans.Authentication;
import databeans.AuthenticationType;
import databeans.MessageWrapper;
import databeans.MethodType;
import util.JSONParser;

import java.util.HashMap;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.NewPinCard.doNewPinCardRequest;
import static api.methods.SharedUtilityMethods.sendErrorReply;
import static api.methods.SharedUtilityMethods.valueHasCorrectLength;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class GetAuthToken {

    /**
     * Logs a user into the system and sends the user an authToken to authorize himself.
     * @param params Parameters of the request (username, password).
     * @param api DataBean containing everything in the ApiService
     */
    public static void getAuthToken(final Map<String, Object> params, final ApiBean api) {
        System.out.printf("%s Logging in.\n", PREFIX);
        handleLoginExceptions(new Authentication((String) params.get("username"),
                (String) params.get("password")), null, api);
    }

    /**
     * Performs a login request to fetch an authentication that can be used to request a new pin card, then performs a
     * new pin card request.
     * @param authentication Username & Password DataBean to login with.
     * @param accountNumber AccountNumber the new pin card should be requested for.
     * @param api DataBean containing everything in the ApiService
     */
    public static void getAuthTokenForPinCard(final Authentication authentication,
            final String accountNumber, final ApiBean api) {
        System.out.printf("%s Logging in.\n", PREFIX);
        handleLoginExceptions(authentication, accountNumber, api);
    }

    /**
     * Tries to verify the input of the login request and then forwards the request to the Authentication Service,
     * rejects the request if an exception is thrown.
     * @param authData {@link Authentication} which contains the login information of a User.
     * @param createPin null if no new Pin card needs to be created, otherwise an accountNumber
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleLoginExceptions(
            final Authentication authData, final String createPin, final ApiBean api) {
        try {
            verifyLoginInput(authData);
            doLoginRequest(authData, createPin, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s %s", PREFIX, e.getMessage());
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value."), api);
        }
    }

    /**
     * Checks if the input for a login request is correctly formatted and contains correct values.
     * @param authentication {@link Authentication} information of a user trying to login.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     */
    private static void verifyLoginInput(final Authentication authentication)
            throws IncorrectInputException, JsonSyntaxException {
        final String username = authentication.getUsername();
        final String password = authentication.getPassword();
        final AuthenticationType authenticationType = authentication.getType();
        if (username == null || !valueHasCorrectLength(username)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: username.");
        } else if (password == null || !valueHasCorrectLength(password)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: password.");
        } else if ((authenticationType == null) || (authenticationType != AuthenticationType.LOGIN)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: authenticationType.");
        }
    }

    /**
     * Forwards the Login request to the Authentication Service and sends a callback if the request is successful, or
     * sends a rejection if the request fails.
     * @param authData {@link Authentication} information of a user trying to login.
     * @param createPin null if no new Pin card needs to be created, otherwise an accountNumber
     * @param api DataBean containing everything in the ApiService
     */
    private static void doLoginRequest(final Authentication authData, final String createPin, final ApiBean api) {
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/login",
                "authData", api.getJsonConverter().toJson(authData), (code, contentType, body) -> {
                    if (code == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            sendLoginRequestCallback((Authentication) messageWrapper.getData(), createPin, api);
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
     * Sends the result of a successful login request, containing a cookie that the user should use to authenticate
     * him/herself to the request source.
     * @param loginReply {@link Authentication} object containing the cookie the customer should use
     * to authenticate himself in future requests.
     * @param createPin null if no new Pin card needs to be created, otherwise an accountNumber
     * @param api DataBean containing everything in the ApiService
     */
    private static void sendLoginRequestCallback(
            final Authentication loginReply, final String createPin, final ApiBean api) {
        System.out.printf("%s Successful login, set the following cookie: %s\n\n\n\n",
                PREFIX, loginReply.getCookie());
        if (createPin != null) {
            doNewPinCardRequest(MethodType.GET_AUTH_TOKEN, createPin, loginReply.getUsername(), loginReply.getCookie(), api, true);
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("authToken", loginReply.getCookie());
            api.getCallbackBuilder().build().reply(new JSONRPC2Response(result, api.getId()).toJSONString());
        }
    }
}
