package authentication;

/**
 * @author Saul
 */
public class AccountFrozenException extends Exception {
    AccountFrozenException(final String message) {
        super(message);
    }
}
