package main.util;

import com.google.gson.Gson;

public class JsonRpcBuilder {

    public static String getJsonRpc(String method, Object object) {
        JsonRpcObject rpc = new JsonRpcObject();
        rpc.setObject(method, object);
        return new Gson().toJson(rpc);
    }

}
