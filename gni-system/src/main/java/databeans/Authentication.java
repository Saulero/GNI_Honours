package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public class Authentication implements Serializable {

    private AuthenticationType type;
    private long userID;
    private String username;
    private String password;
    private String cookie;

    public Authentication(long userID, String username, String password) {
        this.type = AuthenticationType.CREATENEW;
        this.userID = userID;
        this.username = username;
        this.password = password;
        this.cookie = null;
    }

    public Authentication(String username, String password) {
        this.type = AuthenticationType.LOGIN;
        this.userID = -1;
        this.username = username;
        this.password = password;
        this.cookie = null;
    }

    public Authentication(String cookie, AuthenticationType type) {
        this.type = type;
        this.userID = -1;
        this.username = null;
        this.password = null;
        this.cookie = cookie;
    }

    public Authentication() { }

    public AuthenticationType getType() {
        return type;
    }

    public void setType(AuthenticationType type) {
        this.type = type;
    }

    public long getUserID() {
        return userID;
    }

    public void setUserID(long userID) {
        this.userID = userID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Authentication that = (Authentication) o;

        if (userID != that.userID) return false;
        if (type != that.type) return false;
        if (username != null ? !username.equals(that.username) : that.username != null) return false;
        if (password != null ? !password.equals(that.password) : that.password != null) return false;
        return cookie != null ? cookie.equals(that.cookie) : that.cookie == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (int) (userID ^ (userID >>> 32));
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (cookie != null ? cookie.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Authentication{" +
                "type=" + type +
                ", userID=" + userID +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", cookie='" + cookie + '\'' +
                '}';
    }
}
