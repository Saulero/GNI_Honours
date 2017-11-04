package main.client;

import main.util.JsonRpcBuilder;
import main.util.JsonRpcChecker;
import main.util.Methods;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class SocketClient implements IClient {

    Socket s = null;
    BufferedReader input = null;
    PrintWriter out = null;

    private int requestCount = 0;

    public SocketClient() {
        try {
            s = new Socket(InetAddress.getByName("localhost"), 9090);
            input = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintWriter(s.getOutputStream(), true);
        } catch (Exception e) {
            System.err.println("Could not instantiate SocketClient");
        }
    }

    public String processRequest(Methods method, Object request) {
        requestCount++;
        String requestString = JsonRpcBuilder.getJsonRpc(method.name(), request);
        out.println(requestString);
        try {
            String resultString = input.readLine();
            JsonRpcChecker.checkJsonRpcMessage(requestString);
            return requestString;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getRequestCount() {
        return requestCount;
    }
}
