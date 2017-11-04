package main.util;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class Checker {

    /**
     * Checks if a given return message has an empty result field and no error field.
     *
     * @param message the return json rpc message from the server
     */
    public static void checkSuccess(String message) {
        assertThat(message, hasJsonPath("result.length()", equalTo(0)));
        assertThat(message, hasNoJsonPath("error"));
    }

    /**
     * Checks if the return message contains an error field and checks if this object has a code field with corresponding
     * errorCode. It also check that the message does not contain a result field.
     *
     * @param message   the return json rpc message from the server
     * @param errorCode errorCode the message should contain
     */
    public static void checkError(String message, int errorCode) {
        checkError(message);
        assertThat(message, hasJsonPath("error.code", equalTo(errorCode)));
    }

    /**
     * Checks if the return message contains no result field and does contain a error field. This method does not check if
     * the error field has any contents
     *
     * @param message the return json rpc message from the server
     */
    public static void checkError(String message) {
        assertThat(message, hasNoJsonPath("result"));
        assertThat(message, hasJsonPath("error"));
    }
}
