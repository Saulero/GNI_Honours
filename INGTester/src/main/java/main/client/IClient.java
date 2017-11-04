package main.client;


import main.util.Methods;

public interface IClient {

    String processRequest(Methods method, Object request);

    int getRequestCount();

}


