package main.client;

import main.util.JsonRpcChecker;
import main.util.Methods;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static main.util.JsonRpcBuilder.getJsonRpc;

public class TestHttpClient implements IClient {

    private String url;

    public static final HttpClient httpClient = HttpClientBuilder.create().build();

    private int requestCount = 0;

    public TestHttpClient(String url) {
        this.url = url;
    }

    public String processRequest(Methods method, Object object) {
        try {
            requestCount++;
            HttpPost request = new HttpPost(url);
            request.setHeader("content-type", "application/x-www-form-urlencoded");
            request.setEntity(new StringEntity(getJsonRpc(method.name(), object)));
            HttpResponse response = httpClient.execute(request);
            String result = EntityUtils.toString(response.getEntity());
            result = removeEscapeCharacters(result);
            JsonRpcChecker.checkJsonRpcMessage(result);
            return result;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Removes escape characters from a string, this is done to be able to parse json strings received through
     * a callback, as a callback adds escape characters to the json string.
     * @param dataString Json to remove escape characters from.
     * @return Json string without escape characters.
     */
    public static String removeEscapeCharacters(final String dataString) {
        char[] characters = dataString.substring(1, dataString.length() - 1).toCharArray();
        StringBuilder stringWithoutEscapes = new StringBuilder();
        for (int i = 0; i < characters.length; i++) {
            if (characters[i] == '\\') {
                stringWithoutEscapes.append(characters[i + 1]);
                i++;
            } else {
                stringWithoutEscapes.append(characters[i]);
            }
        }
        return stringWithoutEscapes.toString();
    }
}
