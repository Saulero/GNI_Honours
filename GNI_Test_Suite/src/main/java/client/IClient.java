package client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public interface IClient {

    public JSONRPC2Response processRequest(JSONRPC2Request request);
}


