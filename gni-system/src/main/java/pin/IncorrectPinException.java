package pin;

/**
 * @Author noel
 */
public class IncorrectPinException extends Exception {
    IncorrectPinException(final String message) {
        super(message);
    }
}