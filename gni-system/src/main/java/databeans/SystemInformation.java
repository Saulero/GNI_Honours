package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public class SystemInformation implements Serializable {

    private ServiceInformation apiServiceInformation;
    private ServiceInformation authenticationServiceInformation;
    private ServiceInformation ledgerServiceInformation;
    private ServiceInformation pinServiceInformation;
    private ServiceInformation transactionReceiveServiceInformation;
    private ServiceInformation transactionDispatchServiceInformation;
    private ServiceInformation usersServiceInformation;

    public void setNewServiceInformation(final ServiceInformation serviceInformation) {
        switch (serviceInformation.getServiceType()) {
            case API_SERVICE:
                apiServiceInformation = serviceInformation;
                break;
            case AUTHENTICATION_SERVICE:
                authenticationServiceInformation = serviceInformation;
                break;
            case LEDGER_SERVICE:
                ledgerServiceInformation = serviceInformation;
                break;
            case PIN_SERVICE:
                pinServiceInformation = serviceInformation;
                break;
            case TRANSACTION_RECEIVE_SERVICE:
                transactionReceiveServiceInformation = serviceInformation;
                break;
            case TRANSACTION_DISPATCH_SERVICE:
                transactionDispatchServiceInformation = serviceInformation;
                break;
            case USERS_SERVICE:
                usersServiceInformation = serviceInformation;
                break;
            default:
                break;
        }
    }

    public boolean isComplete() {
        return apiServiceInformation != null
                && authenticationServiceInformation != null
                && ledgerServiceInformation != null
                && pinServiceInformation != null
                && transactionReceiveServiceInformation != null
                && transactionDispatchServiceInformation != null
                && usersServiceInformation != null;
    }

    public ServiceInformation getApiServiceInformation() {
        return apiServiceInformation;
    }

    public ServiceInformation getAuthenticationServiceInformation() {
        return authenticationServiceInformation;
    }

    public ServiceInformation getLedgerServiceInformation() {
        return ledgerServiceInformation;
    }

    public ServiceInformation getPinServiceInformation() {
        return pinServiceInformation;
    }

    public ServiceInformation getTransactionReceiveServiceInformation() {
        return transactionReceiveServiceInformation;
    }

    public ServiceInformation getTransactionDispatchServiceInformation() {
        return transactionDispatchServiceInformation;
    }

    public ServiceInformation getUsersServiceInformation() {
        return usersServiceInformation;
    }
}
