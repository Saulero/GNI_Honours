package authentication;

/**
 * @author Noel
 */
public class UsernameTakenException extends Exception {
    UsernameTakenException(final String message) {
        super(message);
    }
}
