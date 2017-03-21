package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public enum AuthenticationType implements Serializable {
    /** Used when a user is not yet logged in, or has to login again due to an expired token. */
    LOGIN,
    /** Used when a user is already logged in and the token needs to be authenticated. */
    AUTHENTICATE,
    /** Used when a new user is created and it's authentication data has just been created. */
    CREATENEW,
    /** Reply sent with data for user cookie */
    REPLY
}
