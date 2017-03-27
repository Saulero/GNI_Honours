package authentication;

import com.google.gson.Gson;
import database.ConnectionPool;
import database.SQLConnection;
import databeans.*;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import util.JSONParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import static database.SQLStatements.*;

/**
 * @author Saul
 * @version 1
 */
@RequestMapping("/authentication")
class AuthenticationService {

    /** Database connection pool containing persistent database connections. */
    private ConnectionPool db;
    /** Secure Random Number Generator. */
    private SecureRandom sRand;

    /**
     * Constructor.
     */
    AuthenticationService() {
        this.db = new ConnectionPool();
        this.sRand = new SecureRandom();
    }

    // TODO Should be invoked when a new user is created
    /**
     * Creates new login credentials for a customer.
     * @param callback Used to send a reply back to the UserService
     * @param body Json String representing login information
     */
    @RequestMapping(value = "/register", method = RequestMethod.PUT)
    public void register(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Authentication authData = gson.fromJson(body, Authentication.class);

        if (authData.getType() == AuthenticationType.CREATENEW) {
            try {
                SQLConnection connection = db.getConnection();
                PreparedStatement ps = connection.getConnection().prepareStatement(createAuthenticationData);
                ps.setLong(1, authData.getUserID());       // id
                ps.setString(2, authData.getUsername());    // username
                ps.setString(3, authData.getPassword());    // password

                ps.executeUpdate();
                ps.close();
                db.returnConnection(connection);
                callback.reply(gson.toJson(authData));
                // TODO Empty callback would suffice
            } catch (SQLException e) {
                callback.reject(e.getMessage());
                e.printStackTrace();
            }
        } else {
            callback.reject("Wrong Data Received");
        }
    }

    // TODO Should be invoked when login credentials are entered
    /**
     * Checks the login credentials and generates a login token if they're correct.
     * @param callback
     * @param body Json String representing login information
     */
    @RequestMapping(value = "/login", method = RequestMethod.PUT)
    public void login(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Authentication authData = gson.fromJson(body, Authentication.class);

        if (authData.getType() == AuthenticationType.LOGIN) {
            try {
                SQLConnection connection = db.getConnection();
                PreparedStatement ps = connection.getConnection().prepareStatement(getAuthenticationData1);
                ps.setString(1, authData.getUsername());    // username
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    long userID = rs.getLong("user_id");
                    String password = rs.getString("password");

                    if (password.equals(authData.getPassword())) {
                        // Legitimate info
                        // TODO forward request / refer user to correct page
                        long newToken = sRand.nextLong();
                        updateToken(userID, newToken);
                        callback.reply(gson.toJson(new Authentication(encodeCookie(userID, newToken), AuthenticationType.REPLY)));
                    } else {
                        // Illegitimate info
                        callback.reject("Invalid username/password combination");
                    }
                } else {
                    // username not found
                    callback.reject("Username not found");
                }

                rs.close();
                ps.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
                callback.reject(e.getMessage());
                e.printStackTrace();
            }
        } else {
            callback.reject("Wrong Data Received");
        }
    }

    // TODO Should be invoked when a user is already logged in
    /**
     * Authenticates a request by checking the token.
     * @param callback
     * @param body Json String representing the token
     */
    @RequestMapping(value = "/authenticate", method = RequestMethod.PUT)
    public void authenticate(final Callback<String> callback, final @RequestParam("body") String body) {
        Gson gson = new Gson();
        Authentication authData = gson.fromJson(body, Authentication.class);

        if (authData.getType() == AuthenticationType.AUTHENTICATE) {
            Long[] cookieData = decodeCookie(authData.getCookie());
            long userID = cookieData[0];
            long tokenGiven = cookieData[1];

            try {
                SQLConnection connection = db.getConnection();
                PreparedStatement ps = connection.getConnection().prepareStatement(getAuthenticationData2);
                ps.setLong(1, userID);    // user_id
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    long tokenActual = rs.getLong("token");
                    long validity = rs.getLong("token_validity");

                    if (tokenGiven == tokenActual) {
                        // Legitimate token
                        if (System.currentTimeMillis() < validity) {
                            // Valid token
                            updateTokenValidity(userID);
                            // TODO forward request / refer user to correct page
                            // TODO Empty callback would suffice
                            callback.reply(gson.toJson(authData));
                        } else {
                            // Expired token
                            callback.reject("Expired token");
                        }
                    } else {
                        // Illegitimate token
                        callback.reject("Invalid token");
                    }
                } else {
                    // username not found
                    callback.reject("UserID not found");
                }

                rs.close();
                ps.close();
                db.returnConnection(connection);
            } catch (SQLException e) {
                callback.reject(e.getMessage());
                e.printStackTrace();
            }
        } else {
            callback.reject("Wrong Data Received");
        }
    }

    private Long[] decodeCookie(final String cookie) {
        String[] cookieParts = cookie.split(":");
        Long[] res = new Long[2];
        res[0] = Long.parseLong(cookieParts[0]);
        res[1] = Long.parseLong(new String(Base64.getDecoder().decode(cookieParts[1].getBytes())));
        return res;
    }

    private String encodeCookie(final long userID, final long token) {
        return "" + userID + ":" + new String(Base64.getEncoder().encode(("" + token).getBytes()));
    }

    /**
     * Overwrites old token (if any) with a new one and updates the validity.
     * @param id The user_id of the row to update
     * @param token The token to set
     */
    private void updateToken(final long id, final long token) {
        long validity = System.currentTimeMillis() + Variables.TOKEN_VALIDITY * 1000;

        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateToken);
            ps.setLong(1, token);       // new token
            ps.setLong(2, validity);    // validity
            ps.setLong(3, id);          // id
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the validity of the token upon reuse.
     * @param id The user_id of the row to update
     */
    private void updateTokenValidity(final long id) {
        long validity = System.currentTimeMillis() + Variables.TOKEN_VALIDITY * 1000;

        try {
            SQLConnection connection = db.getConnection();
            PreparedStatement ps = connection.getConnection().prepareStatement(updateTokenValidity);
            ps.setLong(1, validity);    // validity
            ps.setLong(2, id);          // id
            ps.executeUpdate();

            ps.close();
            db.returnConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Safely shuts down the AuthenticationService.
     */
    public void shutdown() {
        db.close();
    }
}
