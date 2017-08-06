package client;

import client.IClient;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class DummyClient implements IClient {



    public JSONRPC2Response processRequest(JSONRPC2Request request) {

        // Step 1. Finish the Request.
        String stringRequest = request.toString();

        // Step 2. Send the REquest. Await the response.
        String stringResponse = server.DummyServer.processRequest(stringRequest);

        // Step 3. Parse the response.

        System.out.println("Client. ProcessResponse: "+stringResponse);
        // Parse response string
        JSONRPC2Response respIn = null;

//        if(stringResponse==null){
//            thr
//        }

        try {
            respIn = JSONRPC2Response.parse(stringResponse);
        } catch (JSONRPC2ParseException e) {
            System.out.println(e.getMessage());
        }




        return respIn;
    }




}
