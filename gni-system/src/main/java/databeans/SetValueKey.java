package databeans;

import api.IncorrectInputException;

import java.io.Serializable;

/**
 * @author Saul
 */
public enum SetValueKey implements Serializable {

    CREDIT_CARD_MONTHLY_FEE,
    CREDIT_CARD_DEFAULT_CREDIT,
    CARD_EXPIRATION_LENGTH,
    NEW_CARD_COST,
    CARD_USAGE_ATTEMPTS,
    MAX_OVERDRAFT_LIMIT,
    INTEREST_RATE_1,
    INTEREST_RATE_2,
    INTEREST_RATE_3,
    OVERDRAFT_INTEREST_RATE,
    DAILY_WITHDRAW_LIMIT,
    WEEKLY_TRANSFER_LIMIT;

    public static SetValueKey getValue(final String s) throws IncorrectInputException {
        switch (s) {
            case "CREDIT_CARD_MONTHLY_FEE":         return SetValueKey.CARD_EXPIRATION_LENGTH;
            case "CREDIT_CARD_DEFAULT_CREDIT":      return SetValueKey.CREDIT_CARD_DEFAULT_CREDIT;
            case "CARD_EXPIRATION_LENGTH":          return SetValueKey.CARD_EXPIRATION_LENGTH;
            case "NEW_CARD_COST":                   return SetValueKey.NEW_CARD_COST;
            case "CARD_USAGE_ATTEMPTS":             return SetValueKey.CARD_USAGE_ATTEMPTS;
            case "MAX_OVERDRAFT_LIMIT":             return SetValueKey.MAX_OVERDRAFT_LIMIT;
            case "INTEREST_RATE_1":                 return SetValueKey.INTEREST_RATE_1;
            case "INTEREST_RATE_2":                 return SetValueKey.INTEREST_RATE_2;
            case "INTEREST_RATE_3":                 return SetValueKey.INTEREST_RATE_3;
            case "OVERDRAFT_INTEREST_RATE":         return SetValueKey.OVERDRAFT_INTEREST_RATE;
            case "DAILY_WITHDRAW_LIMIT":            return SetValueKey.DAILY_WITHDRAW_LIMIT;
            case "WEEKLY_TRANSFER_LIMIT":           return SetValueKey.WEEKLY_TRANSFER_LIMIT;
            default:
                throw new IncorrectInputException("Key not recognized.");
        }
    }

    public boolean isLedgerKey() {
        switch (this) {
            case MAX_OVERDRAFT_LIMIT:       return true;
            case INTEREST_RATE_1:           return true;
            case INTEREST_RATE_2:           return true;
            case INTEREST_RATE_3:           return true;
            case OVERDRAFT_INTEREST_RATE:   return true;
            case DAILY_WITHDRAW_LIMIT:      return true;
            case WEEKLY_TRANSFER_LIMIT:     return true;
            default:                        return false;
        }
    }
}
