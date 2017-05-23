package authentication;

/**
 * @author Noel
 */
public class IncorrectLoginException extends Exception {
    IncorrectLoginException(final String message) {
        super(message);
    }
}
