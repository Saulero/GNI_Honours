package pin;

/**
 * @Author noel
 */
public class CardExpiredException extends Exception {
    CardExpiredException(final String message) {
        super(message);
    }
}
