import io.advantageous.qbit.annotation.Listen;
import static io.advantageous.qbit.service.ServiceContext.serviceContext;

/**
 * Created by noel on 4-2-17.
 * The User microservice, handles customer information.
 * Creates customer accounts.
 * Initiates transactions for customers
 */
class UserService {

    /**
     * Listens on USER_CREATION_CHANNEL for new customer creation requests and adds these to the database.
     * @param customer customer to add to the databse.
     */
    @Listen(ServiceManager.USER_CREATION_CHANNEL)
    void enrollCustomer(final Customer customer) {
        //TODO write code to enroll customer in database.
        System.out.printf("Users: Enrolled new customer: %s %s\n", customer.getName(), customer.getSurname());
    }

    /**
     * Listens on DATA_REQUEST_CHANNEL for services that request user data.
     * If the data is customer information loads this data from the database and sends the information
     * back in a dataReply object using DATA_REPLY_CHANNEL
     * @param dataRequest request objects containing the request type, and the account number the request is for
     */
    @Listen(ServiceManager.DATA_REQUEST_CHANNEL)
    void process_data_request(final DataRequest dataRequest) {
        DataRequest.requestType requestType = dataRequest.getType();
        if (requestType == DataRequest.requestType.CUSTOMERDATA) {
            //TODO fetch customer information form database
            String customerInformation = "freekje";
            DataReply dataReply = new DataReply(dataRequest.getAccountNumber(), requestType, customerInformation);
            serviceContext().send(ServiceManager.DATA_REPLY_CHANNEL, dataReply);
        }
    }
}

class Customer {
    private String name;
    private String surname;
    private String accountNumber;

    Customer(String name, String surname, String accountNumber) {
        this.name = name;
        this.surname = surname;
        this.accountNumber = accountNumber;
    }

    String getName(){return this.name;}

    String getSurname(){return this.surname;}

    String getAccountNumber(){return this.accountNumber;}
}
