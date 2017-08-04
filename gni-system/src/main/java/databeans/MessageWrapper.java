package databeans;

import java.io.*;
import java.util.Base64;

/**
 * @author Saul
 */
public class MessageWrapper implements Serializable {

    private boolean error;
    private int code;
    private String message;
    private byte[] data;

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
        if (data != null) {
            return deserialize(Base64.getDecoder().decode(data));
        } else {
            return null;
        }
    }

    public void setData(Object data) {
        byte[] bytes = serialize(data);
        if (bytes != null) {
            this.data = Base64.getEncoder().encode(bytes);
        }
    }

    private byte[] serialize(Object obj) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ObjectOutputStream o = new ObjectOutputStream(b);
            o.writeObject(obj);
            return b.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Object deserialize(byte[] bytes) {
        try {
            ByteArrayInputStream b = new ByteArrayInputStream(bytes);
            ObjectInputStream o = new ObjectInputStream(b);
            return o.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
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
