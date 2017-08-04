package databeans;

import java.io.Serializable;

/**
 * @author Saul
 */
public class MessageWrapper implements Serializable {

    private boolean error;
    private int code;
    private String message;
    private Object data;

    public MessageWrapper() {

    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "MessageWrapper{" +
                "error=" + error +
                ", code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
