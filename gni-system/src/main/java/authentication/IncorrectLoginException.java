package authentication;

/**
 * Created by noel on 28-3-17.
 */
public class IncorrectLoginException extends Exception {
    IncorrectLoginException(final String message) {
        super(message);
    }
}
