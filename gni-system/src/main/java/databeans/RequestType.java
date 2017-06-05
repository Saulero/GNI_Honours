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
    /** Used to retrieve data about which account belongs to a certain customer. */
    ACCOUNTS,
    /** Used to check if an account exists. */
    ACCOUNTEXISTS,
    /** Used to fetch the owners of a list of accounts. */
    OWNERS
}
