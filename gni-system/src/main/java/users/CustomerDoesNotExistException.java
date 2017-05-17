package users;

/**
 * @author Noel
 */
public class CustomerDoesNotExistException extends Exception {
    CustomerDoesNotExistException(final String message) {
        super(message);
    }
}
