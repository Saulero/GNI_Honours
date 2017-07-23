package client;

import client.IClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.HttpClients;
import org.apache.hc.client5.http.methods.HttpPost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.StringEntity;

import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class TestHttpClient implements IClient {
	// TODO: Replace below with the location of your web application/service
	public static final String SERVICE_URL = "http://localhost:9997/services/api/request";

	CloseableHttpClient httpclient = HttpClients.createDefault();

	public JSONRPC2Response stringToJSON(String jResponse) {
		// If using a different JSON-RPC library, replace the return type and below code as needed
		try {
			return JSONRPC2Response.parse(jResponse);
		} catch (JSONRPC2ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	//If using a different library, replace return type with your (library's) JSON-RPC response object 
    public JSONRPC2Response processRequest(JSONRPC2Request request) {
		String message = request.toJSONString();
	
		HttpPost httpPost = new HttpPost(SERVICE_URL);
		StringEntity msg = new StringEntity(message, ContentType.create("application/x-www-form-urlencoded"));
		httpPost.setEntity(msg);
		
		try {
			HttpResponse x = httpclient.execute(httpPost);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					(x.getEntity().getContent())));
			String out, output = "";
			while ((out = reader.readLine()) != null) {
				output += out;
			}
			return stringToJSON(output);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
    }




}
