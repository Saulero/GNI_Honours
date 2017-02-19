package util;

/**
 * Created by noel on 18-2-17.
 */
public class Util {

    public static DataReply createJsonReply(String accountNumber, RequestType type, String data) {
        DataReply reply = new DataReply();
        reply.setAccountNumber(accountNumber);
        reply.setType(type);
        reply.setData(data);
        return reply;
    }

    public static DataRequest createJsonRequest(String accountNumber, RequestType type) {
        DataRequest request = new DataRequest();
        request.setType(type);
        request.setAccountNumber(accountNumber);
        return request;
    }
}
