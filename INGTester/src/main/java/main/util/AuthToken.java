package main.util;

import com.jayway.jsonpath.JsonPath;
import main.client.IClient;
import main.model.methods.GetAuthToken;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static main.util.Constants.ADMIN_PASSWORD;
import static main.util.Constants.ADMIN_USERNAME;
import static main.util.Methods.getAuthToken;
import static org.junit.Assert.assertThat;

public class AuthToken {

    public static String getAuthToken(IClient client, String username, String password) {
        String loginResult = client.processRequest(getAuthToken, new GetAuthToken(username, password));
        assertThat(loginResult, hasJsonPath("result"));
        assertThat(loginResult, hasNoJsonPath("error"));
        assertThat(loginResult, hasJsonPath("result.authToken"));
        return JsonPath.read(loginResult, "result.authToken");
    }

    public static String getAdminLoginToken(IClient client) {
        return getAuthToken(client, ADMIN_USERNAME, ADMIN_PASSWORD);
    }

}
