package users;

import io.advantageous.qbit.annotation.Listen;
import queue.ServiceManager;
import ui.DataReply;
import ui.DataRequest;
import ui.RequestType;

import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * @author Noel
 * @version 1
 * The User microservice, handles customer information.
 * Creates customer accounts.
 * Initiates transactions for customers
 */
public class UserService {

    /**
     * Listens on USER_CREATION_CHANNEL for new customer creation requests
     * and adds these to the database.
     * @param customer customer to add to the databse.
     */
    @Listen(ServiceManager.USER_CREATION_CHANNEL)
    public void enrollCustomer(final Customer customer) {
        //TODO write code to enroll customer in database.
        System.out.printf("Users: Enrolled new customer: %s %s\n",
                        customer.getName(), customer.getSurname());
    }

    /**
     * Listens on DATA_REQUEST_CHANNEL for services that request user data.
     * If the data is customer information loads this data from the database
     * and sends the information back in a dataReply object using
     * DATA_REPLY_CHANNEL.
     * @param dataRequest request objects containing the request type,
     *                    and the account number the request is for.
     */
    @Listen(ServiceManager.DATA_REQUEST_CHANNEL)
    public void processDataRequest(final DataRequest dataRequest) {
        RequestType requestType = dataRequest.getType();
        if (requestType == RequestType.CUSTOMERDATA) {
            //TODO fetch customer information form database
            String customerInformation = "freekje";
            DataReply dataReply = new DataReply(dataRequest.getAccountNumber(),
                                                requestType,
                                                customerInformation);
            serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);
        }
    }
}
