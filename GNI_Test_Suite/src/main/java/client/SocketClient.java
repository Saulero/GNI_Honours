package client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class SocketClient implements IClient{

    Socket s = null;
    BufferedReader input = null;
    PrintWriter out = null;

    public SocketClient(){

        try {
            s = new Socket(InetAddress.getByName("localhost"), 9090);

            input =
                    new BufferedReader(new InputStreamReader(s.getInputStream()));

            out = new PrintWriter(s.getOutputStream(), true);
        } catch (Exception e){
            System.out.println("Errors");
        }
    }

    public JSONRPC2Response processRequest(JSONRPC2Request request) {

        // Step 1. Finish the Request.
        String stringRequest = request.toString();

        // Step 2. Send the REquest. Await the response.
        out.println(stringRequest);

        JSONRPC2Response respIn = null;

        try {
            String stringResponse = input.readLine();

            // Step 3. Parse the response.

            System.out.println("Client. ProcessResponse: " + stringResponse);
            // Parse response string


            try {
                respIn = JSONRPC2Response.parse(stringResponse);
            } catch (JSONRPC2ParseException e) {
                System.out.println(e.getMessage());
            }
        } catch (Exception e){
            System.out.println("Errors!");
        }

        return respIn;
    }

}
