package users;

/**
 * Created by noel on 23-3-17.
 */
public class CustomerDoesNotExistException extends Exception {
    CustomerDoesNotExistException(final String message) {
        super(message);
    }
}
