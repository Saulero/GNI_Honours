package api.methods;

import api.ApiBean;
import api.IncorrectInputException;
import com.google.gson.JsonSyntaxException;
import databeans.Account;
import databeans.Authentication;
import databeans.Customer;
import databeans.MessageWrapper;
import util.JSONParser;

import java.time.LocalDate;
import java.util.Map;

import static api.ApiService.PREFIX;
import static api.methods.GetAuthToken.getAuthTokenForPinCard;
import static api.methods.SharedUtilityMethods.sendErrorReply;
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
     * @param api DataBean containing everything in the ApiService
     */
    public static void openAccount(final Map<String, Object> params, final ApiBean api) {
        String date = (String) params.get("dob");
        String[] dobData = date.split("-");
        LocalDate dob = LocalDate.of(Integer.parseInt(dobData[0]), Integer.parseInt(dobData[1]), Integer.parseInt(dobData[2]));
        Customer customer = new Customer((String) params.get("initials"), (String) params.get("name"),
                (String) params.get("surname"), (String) params.get("email"), (String) params.get("telephoneNumber"),
                (String) params.get("address"), dob, Long.parseLong((String) params.get("ssn")),
                (String) params.get("username"), (String) params.get("password"));
        String type = (String) params.get("type");
        if (type != null && type.equals("child")) {
            // sets both the Child flag to true, and adds the guardians
            customer.setGuardians((String[]) params.get("guardians"));
        }
        handleNewCustomerExceptions(customer, api);
    }

    /**
     * Tries to verify the input of a new customer request and then forward the request, sends a rejection if an
     * exception occurs.
     * @param newCustomer {@link Customer} that is to be created in the system.
     * @param api DataBean containing everything in the ApiService
     */
    private static void handleNewCustomerExceptions(final Customer newCustomer, final ApiBean api) {
        try {
            verifyNewCustomerInput(newCustomer);
            verifyAgeInput(api, newCustomer);
        } catch (IncorrectInputException e) {
            System.out.printf("%s One of the parameters has an invalid value, sending error.", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the parameters has an invalid value.", e.getMessage()), api);
        } catch (JsonSyntaxException e) {
            System.out.printf("%s The json received contained incorrect syntax, sending rejection.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418, "Syntax error when parsing json."), api);
        } catch (NumberFormatException e) {
            System.out.printf("%s The ssn, overdraft limit or balance was incorrectly specified.\n", PREFIX);
            sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                    "One of the following variables was incorrectly specified: ssn, overdraft limit, balance."), api);
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
        final LocalDate dob = newCustomer.getDob();
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
        } else if (dob == null) {
            throw new IncorrectInputException("The following variable was incorrectly specified: dob.");
        } else if (ssn < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: ssn.");
        } else if (newCustomer.getAccount() == null && newCustomer.getAccount().getOverdraftLimit() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: overdraft limit.");
        } else if (newCustomer.getAccount() == null && newCustomer.getAccount().getBalance() < 0) {
            throw new IncorrectInputException("The following variable was incorrectly specified: balance.");
        } else if (username == null || !valueHasCorrectLength(username)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: username.");
        } else if (password == null || !valueHasCorrectLength(password)) {
            //todo specify more formal password requirements
            throw new IncorrectInputException("The following variable was incorrectly specified: password.");
        } else if (newCustomer.isChild() && (newCustomer.getGuardians() == null || newCustomer.getGuardians().length < 1)) {
            throw new IncorrectInputException("The following variable was incorrectly specified: guardians.");
        }
    }

    private static void verifyAgeInput(final ApiBean api, final Customer newCustomer) {
        LocalDate dob = newCustomer.getDob();
        api.getSystemInformationClient().getAsync("/services/systemInfo/date", (code, contentType, body) -> {
            if (code == HTTP_OK) {
                MessageWrapper messageWrapper = api.getJsonConverter().fromJson(JSONParser.removeEscapeCharacters(body), MessageWrapper.class);
                if (!messageWrapper.isError()) {
                    LocalDate date = (LocalDate) messageWrapper.getData();
                    boolean is18 = false;
                    LocalDate adjustedDob = dob.plusYears(18);
                    if (date.isAfter(adjustedDob)) {
                        is18 = true;
                    }
                    if ((newCustomer.isChild() && !is18) || (!newCustomer.isChild() && is18)) {
                        doNewCustomerRequest(newCustomer, api);
                    } else {
                        System.out.printf("%s One of the parameters has an invalid value, sending error.", PREFIX);
                        sendErrorReply(JSONParser.createMessageWrapper(true, 418,
                                "One of the parameters has an invalid value.",
                                "Primary account holder needs to be 18 for a child account, and over 18 for a normal account."), api);
                    }
                } else {
                    api.getCallbackBuilder().build().reply(body);
                }
            } else {
                api.getCallbackBuilder().build().reply(api.getJsonConverter().toJson(JSONParser.createMessageWrapper(true,
                        500, "An unknown error occurred.",
                        "There was a problem with one of the HTTP requests")));
            }
        });
    }

    /**
     * Sends the customer request to the Authentication service and then processes the reply, or sends a rejection to
     * the source of the request if the request fails..
     * @param customer {@link Customer} that should be created.
     * @param api DataBean containing everything in the ApiService
     */
    private static void doNewCustomerRequest(final Customer customer, final ApiBean api) {
        System.out.printf("%s Forwarding customer creation request.\n", PREFIX);
        api.getAuthenticationClient().putFormAsyncWith1Param("/services/authentication/customer",
                "customer", api.getJsonConverter().toJson(customer),
                (httpStatusCode, httpContentType, newCustomerReplyJson) -> {
                    if (httpStatusCode == HTTP_OK) {
                        MessageWrapper messageWrapper = api.getJsonConverter().fromJson(
                                JSONParser.removeEscapeCharacters(newCustomerReplyJson), MessageWrapper.class);
                        if (!messageWrapper.isError()) {
                            Customer customerReply = (Customer) messageWrapper.getData();
                            getAuthTokenForPinCard(new Authentication(
                                    customerReply.getUsername(), customerReply.getPassword()),
                                    customerReply.getAccount().getAccountNumber(), api);
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
}
