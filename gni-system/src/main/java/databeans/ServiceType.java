package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public enum ServiceType implements Serializable {

    /** Api service. */
    API_SERVICE,

    /** Authentication service. */
    AUTHENTICATION_SERVICE,

    /** Ledger service. */
    LEDGER_SERVICE,

    /** Pin service. */
    PIN_SERVICE,

    /** Transaction Receive service. */
    TRANSACTION_RECEIVE_SERVICE,

    /** Transaction Dispatch service. */
    TRANSACTION_DISPATCH_SERVICE,

    /** Users service. */
    USERS_SERVICE
}
