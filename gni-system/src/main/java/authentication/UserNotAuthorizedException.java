package authentication;

/**
 * Created by noel on 27-3-17.
 */
public class UserNotAuthorizedException extends Exception{
    UserNotAuthorizedException(final String message) {
        super(message);
    }
}
