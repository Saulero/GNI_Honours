package users;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.http.client.HttpClient;
import io.advantageous.qbit.reactive.Callback;
import io.advantageous.qbit.reactive.CallbackBuilder;
import util.*;

import static io.advantageous.qbit.http.client.HttpClientBuilder.httpClientBuilder;

/**
 * @author Noel
 * @version 1
 * The User microservice, handles customer information.
 * Creates customer accounts.
 * Initiates transactions for customers
 */
@RequestMapping("/user")
public class UserService {

    /**
     * Listens on DATA_REQUEST_CHANNEL for services that request user data.
     * If the data is customer information loads this data from the database
     * and sends the information back in a dataReply object using
     * DATA_REPLY_CHANNEL.
     * @param dataRequest request objects containing the request type,
     *                    and the account number the request is for.
     */
    @RequestMapping(value = "/data", method = RequestMethod.GET)
    public void processDataRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        System.out.println("Users: Called by UI, calling Ledger");
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body, DataRequest.class);
        RequestType type = request.getType();
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        if (type == RequestType.CUSTOMERDATA) {
            //TODO fetch customer information from database
            String customerInformation = "freekje";
            DataReply reply = Util.createJsonReply(request.getAccountNumber(), request.getType(), customerInformation);
            callbackBuilder.build().reply(gson.toJson(reply));
        } else {
            HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(9999).build();
            httpClient.start();
            if (type == RequestType.BALANCE) {
                httpClient.getAsyncWith1Param("/services/ledger/data", "body", gson.toJson(request),
                        (code, contentType, replyBody) -> { if (code == 200) {
                            DataReply reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                                            .replaceAll("\\\\", ""), DataReply.class);
                            callbackBuilder.build().reply(gson.toJson(reply));
                        } else {
                            callbackBuilder.build().reject("Recieved an error from ledger.");
                        }
                        });
            } else if (type == RequestType.TRANSACTIONHISTORY) {
                //TODO make ledger request
                System.out.println("Users: Making ledger transactionhistory request");
                httpClient.getAsyncWith1Param("/services/ledger/data", "body", gson.toJson(request),
                        (code, contentType, replyBody) -> { if (code == 200) {
                            System.out.println("received reply, forwarding.");
                            DataReply reply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                                            .replaceAll("\\\\", ""), DataReply.class);
                            callbackBuilder.build().reply(gson.toJson(reply));
                            System.out.println("forwarded");
                        } else {
                            System.out.println("received error, rejecting.");
                            callbackBuilder.build().reject("Recieved an error from ledger.");
                        }
                        });
            } else {
                System.out.println("Received a request of unknown type.");
                callback.reject("Received a request of unknown type.");
            }
        }
    }


    @RequestMapping(value = "/transaction", method = RequestMethod.PUT)
    public void processTransactionRequest(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Transaction transaction = gson.fromJson(body, Transaction.class);
        //TODO send transaction to transactionout
        System.out.println("Sent transaction to transactionOut");
        Transaction transactionInReply = transaction;
        callback.reply(gson.toJson(transactionInReply));
    }

    @RequestMapping(value = "/customer", method = RequestMethod.PUT)
    public void processNewCustomer(final Callback<String> callback, final @RequestParam("body") String body) {
        HttpClient httpClient = httpClientBuilder().setHost("localhost").setPort(9999).build();
        httpClient.start();
        Gson gson = new Gson();
        Customer customer = gson.fromJson(body, Customer.class);
        final CallbackBuilder callbackBuilder = CallbackBuilder.newCallbackBuilder();
        callbackBuilder.withStringCallback(callback);
        httpClient.putFormAsyncWith1Param("/services/ledger/accountNumber", "body", gson.toJson(customer),
                (code, contentType, replyBody) -> { if (code == 200) {
                    Customer ledgerReply = gson.fromJson(replyBody.substring(1, replyBody.length() - 1)
                                                        .replaceAll("\\\\", ""), Customer.class);
                    String accountNumber = ledgerReply.getAccountNumber();
                    boolean enrolled = ledgerReply.getEnrolled();
                    if (enrolled) {
                        String customerName = customer.getName();
                        String customerSurname = customer.getSurname();
                        Customer enrolledCustomer = Util.createJsonCustomer(customerName, customerSurname,
                                accountNumber, enrolled);
                        System.out.println("Sending back new customer.");
                        callbackBuilder.build().reply(gson.toJson(enrolledCustomer));
                    } else {
                        callbackBuilder.build().reject("Ledger failed to enroll.");
                    }
                } else {
                    callbackBuilder.build().reject("Recieved an error from ledger.");
                }
            });
        //TODO enroll customer in database
    }
}
