package users;

/**
 * @author Saul
 */
public class AccountDoesNotExistException extends Exception {
    public AccountDoesNotExistException(final String message) {
        super(message);
    }
}
