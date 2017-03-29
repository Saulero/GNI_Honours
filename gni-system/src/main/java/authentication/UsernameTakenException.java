package authentication;

/**
 * Created by noel on 29-3-17.
 */
public class UsernameTakenException extends Exception {
    UsernameTakenException(final String message) {
        super(message);
    }
}
