package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public enum MethodType implements Serializable {

    OPEN_ACCOUNT,
    OPEN_ADDITIONAL_ACCOUNT,
    CLOSE_ACCOUNT,
    PROVIDE_ACCESS,
    REVOKE_ACCESS,
    DEPOSIT_INTO_ACCOUNT,
    PAY_FROM_ACCOUNT,
    TRANSFER_MONEY,
    GET_AUTH_TOKEN,
    GET_BALANCE,
    GET_TRANSACTION_OVERVIEW,
    GET_USER_ACCESS,
    GET_BANK_ACCOUNT_ACCESS,
    UNBLOCK_CARD,
    SIMULATE_TIME,
    RESET,
    GET_DATE,
    SET_OVERDRAFT_LIMIT,
    GET_OVERDRAFT_LIMIT,
    GET_EVENT_LOGS,
    OPEN_SAVING_ACCOUNT,
    CLOSE_SAVINGS_ACCOUNT,
    INVALIDATE_CARD,
    REQUEST_CREDIT_CARD,
    SET_FREEZE_USER_ACCOUNT;

    public int getId() {
        switch (this) {
            case OPEN_ACCOUNT:              return 1;
            case OPEN_ADDITIONAL_ACCOUNT:   return 2;
            case CLOSE_ACCOUNT:             return 3;
            case PROVIDE_ACCESS:            return 4;
            case REVOKE_ACCESS:             return 5;
            case DEPOSIT_INTO_ACCOUNT:      return 6;
            case PAY_FROM_ACCOUNT:          return 7;
            case TRANSFER_MONEY:            return 8;
            case GET_AUTH_TOKEN:            return 9;
            case GET_BALANCE:               return 10;
            case GET_TRANSACTION_OVERVIEW:  return 11;
            case GET_USER_ACCESS:           return 12;
            case GET_BANK_ACCOUNT_ACCESS:   return 13;
            case UNBLOCK_CARD:              return 14;
            case SIMULATE_TIME:             return 15;
            case RESET:                     return 16;
            case GET_DATE:                  return 17;
            case SET_OVERDRAFT_LIMIT:       return 18;
            case GET_OVERDRAFT_LIMIT:       return 19;
            case GET_EVENT_LOGS:            return 20;
            case OPEN_SAVING_ACCOUNT:       return 21;
            case CLOSE_SAVINGS_ACCOUNT:     return 22;
            case INVALIDATE_CARD:           return 23;
            case REQUEST_CREDIT_CARD:       return 24;
            case SET_FREEZE_USER_ACCOUNT:   return 25;
            default:                        return -1;
        }
    }

    public boolean isAllowedWhenFrozen() {
        switch (this) {
            case OPEN_ACCOUNT:              return false;
            case OPEN_ADDITIONAL_ACCOUNT:   return false;
            case CLOSE_ACCOUNT:             return false;
            case PROVIDE_ACCESS:            return false;
            case REVOKE_ACCESS:             return false;
            case DEPOSIT_INTO_ACCOUNT:      return false;
            case PAY_FROM_ACCOUNT:          return false;
            case TRANSFER_MONEY:            return false;
            case GET_AUTH_TOKEN:            return true;
            case GET_BALANCE:               return true;
            case GET_TRANSACTION_OVERVIEW:  return true;
            case GET_USER_ACCESS:           return true;
            case GET_BANK_ACCOUNT_ACCESS:   return true;
            case UNBLOCK_CARD:              return false;
            case SIMULATE_TIME:             return true;
            case RESET:                     return true;
            case GET_DATE:                  return true;
            case SET_OVERDRAFT_LIMIT:       return false;
            case GET_OVERDRAFT_LIMIT:       return true;
            case GET_EVENT_LOGS:            return true;
            case OPEN_SAVING_ACCOUNT:       return false;
            case CLOSE_SAVINGS_ACCOUNT:     return false;
            case INVALIDATE_CARD:           return false;
            case REQUEST_CREDIT_CARD:       return false;
            case SET_FREEZE_USER_ACCOUNT:   return false;
            default:                        return false;
        }
    }
}
