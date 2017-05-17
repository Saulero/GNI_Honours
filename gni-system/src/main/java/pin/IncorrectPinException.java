package pin;

/**
 * @author Noel
 */
public class IncorrectPinException extends Exception {
    IncorrectPinException(final String message) {
        super(message);
    }
}