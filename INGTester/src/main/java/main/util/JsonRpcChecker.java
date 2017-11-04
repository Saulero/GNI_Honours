package main.util;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class JsonRpcChecker {

    public static void checkJsonRpcMessage(String message) {
        assertThat(message, hasJsonPath("id", equalTo(Constants.requestID)));
        assertThat(message, hasJsonPath("jsonrpc", equalTo(Constants.jsonRpcVersion)));
    }

}
