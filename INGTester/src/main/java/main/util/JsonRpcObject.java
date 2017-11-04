package main.util;

public class JsonRpcObject {

    private int id = Constants.requestID;
    private String jsonrpc = Constants.jsonRpcVersion;

    private String method;
    private Object params;

    public void setObject(String method, Object params) {
        this.method = method;
        this.params = params;
    }

}
