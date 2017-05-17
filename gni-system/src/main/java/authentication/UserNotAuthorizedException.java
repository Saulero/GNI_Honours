package authentication;

/**
 * @author Noel
 */
public class UserNotAuthorizedException extends Exception{
    UserNotAuthorizedException(final String message) {
        super(message);
    }
}
