package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import databeans.Authentication;
import databeans.Customer;
import databeans.MessageWrapper;
import util.JSONParser;

import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.GetAuthToken.getAuthTokenForPinCard;
import static api.methods.SharedUtilityMethods.valueHasCorrectLength;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * @author Saul
 */
public class OpenAccount {

    /**
     * Creates a customer object that represents the customer an account should be created for. This method will create
     * getAuthTokenForPinCard information for a customer, put the customer's information into the system and create
     * a new bank account for the customer.
     * @param params all parameters for the method call, if a parameter is not in this map the request will be rejected.
     */
    public static void openAccount(final Map<String, Object> params, final ApiBean api) {
        Customer customer = JSONParser.createJsonCustomer((String) params.get("initials"), (String) params.get("name"),
                (String) params.get("surname"), (String) params.get("email"), (String) params.get("telephoneNumber"),
                (String) params.get("address"), (String) params.get("dob"), Long.parseLong((String) params.get("ssn")),
                0.0, 0.0, 0L, (String) params.get("username"),
                (String) params.get("password"));
        handleNewCustomerExceptions(customer, api);
    }

    /**
     * Tries to verify the input of a new customer request and then forward the request, sends a rejection if an
     * exception occurs.
     * @param newCustomer {@link Customer} that is to be created in the system.
     */
    private static void handleNewCustomerExceptions(final Customer newCustomer, ApiBean api) {
        try {
            verifyNewCustomerInput(newCustomer);
            doNewCustomerRequest(newCustomer, api);
        } catch (IncorrectInputException e) {
            System.out.printf("%s One of the parameters has an invalid value, sending error.", PREFIX);
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 418, "One of the parameters has an invalid value.")));
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 418, "Syntax error when parsing json.")));
        } catch (NumberFormatException e) {
            System.out.printf("%s The ssn, spendinglimit or balance was incorrectly specified, sending rejection.\n", PREFIX);
            api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 418, "One of the following variables was incorrectly specified: ssn, spendingLimit, balance.")));
        }
    }

    /**
     * Checks if the input for a new customer request is correctly formatted and contains correct values.
     * @param newCustomer {@link Customer} that is to be created in the system.
     * @throws IncorrectInputException Thrown when a value is not correctly specified.
     * @throws JsonSyntaxException Thrown when the json string is incorrect and cant be parsed.
     * @throws NumberFormatException Thrown when a string value could not be parsed to a Long.
     */
    private static void verifyNewCustomerInput(final Customer newCustomer)
            throws IncorrectInputException, JsonSyntaxException, NumberFormatException {
        final String initials = newCustomer.getInitials();
        final String name = newCustomer.getName();
        final String surname = newCustomer.getSurname();
        final String email = newCustomer.getEmail();
        final String telephoneNumber = newCustomer.getTelephoneNumber();
        final String address = newCustomer.getAddress();
        final String dob = newCustomer.getDob();
        final Long ssn = newCustomer.getSsn();
        final String username = newCustomer.getUsername();
        final String password = newCustomer.getPassword();
        if (initials == null || !valueHasCorrectLength(initials)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: initials.");
        } else if (name == null || !valueHasCorrectLength(name)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: name.");
        } else if (surname == null || !valueHasCorrectLength(surname)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: surname.");
        } else if (email == null || !valueHasCorrectLength(email)) {
            //todo check more formally if its actually an email address
            throw new IncorrectInputException("The following variable was incorrectly specified: email.");
        } else if (telephoneNumber == null || telephoneNumber.length() > 15 || telephoneNumber.length() < 10) {
            throw new IncorrectInputException("The following variable was incorrectly specified: telephoneNumber.");
        } else if (address == null || !valueHasCorrectLength(address)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: address.");
        } else if (dob == null || !valueHasCorrectLength(dob)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: dob.");
        } else if (ssn < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: ssn.");
        } else if (newCustomer.getAccount() == null && newCustomer.getAccount().getSpendingLimit() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: spendingLimit.");
        } else if (newCustomer.getAccount() == null && newCustomer.getAccount().getBalance() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: balance.");
        } else if (username == null || !valueHasCorrectLength(username)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: username.");
        } else if (password == null || !valueHasCorrectLength(password)) {
            //todo specify more formal password requirements
            throw new IncorrectInputException("The following variable was incorrectly specified: password.");
        }
    }

    /**
     * Sends the customer request to the Authentication service and then processes the reply, or sends a rejection to
     * the source of the request if the request fails..
     * @param newCustomer {@link Customer} that should be created.
     */
    private static void doNewCustomerRequest(final Customer customer, final ApiBean api) {
        System.out.printf("%s Forwarding customer creation request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/customer",
                "customer", api.getJsonConverter().toJson(customer),
                (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(newCustomerReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            getAuthTokenForPinCard(new Authentication(customer.getUsername(), customer.getPassword()),
                                    customer.getAccount().getAccountNumber(), api);
                        } else {
                            api.getCallbackBuilder().build().reply(newCustomerReplyJson);
                        }
                    } else {
                        api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true, 500, "An unknown error occurred.", "There was a problem with one of the HTTP requests")));
                    }
                });
    }
}
