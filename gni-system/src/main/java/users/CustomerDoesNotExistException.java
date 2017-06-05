package users;

/**
 * @author Noel
 */
public class CustomerDoesNotExistException extends Exception {
    public CustomerDoesNotExistException(final String message) {
        super(message);
    }
}
