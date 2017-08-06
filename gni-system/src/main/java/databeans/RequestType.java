package databeans;

import java.io.Serializable;

/**
 * @author Noel & Saul
 * @version 1
 * Enum used in DataRequest for specifying what type of request is being done.
 */
public enum RequestType implements Serializable {

    /** Used to retrieve a transaction history from the ledger. */
    TRANSACTIONHISTORY,

    /** Used to retrieve the balance of an account number from the ledger. */
    BALANCE,

    /** Used to retrieve data about a certain customer from the Users service.*/
    CUSTOMERDATA,

    /** Used to check if an account exists. */
    ACCOUNTEXISTS,

    /** Used to fetch the list of accounts for a certain customer. */
    CUSTOMERACCESSLIST,

    /** Used to fetch the access list for a certain account. */
    ACCOUNTACCESSLIST
}
