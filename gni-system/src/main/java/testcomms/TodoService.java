package testcomms;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import util.DataReply;
import util.DataRequest;
import util.Util;
import util.RequestType;

import java.util.*;


@RequestMapping("/todo-service")
public class TodoService {


    private final Map<String, Todo> todoMap = new TreeMap<>();


    @RequestMapping(value = "/todo", method = RequestMethod.POST)
    public void add(final Callback<String> callback, final @RequestParam("body") String body) {
        System.out.println("received a post");
        System.out.println("request: " + body);
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body.substring(1, body.length() - 1).replaceAll("\\\\", ""),
                                            DataRequest.class);
        System.out.printf("Request received, accnr: %s", request.getAccountNumber());
        DataReply reply = Util.createJsonReply(request.getAccountNumber(), request.getType(), "1234567890");
        callback.reply(gson.toJson(reply));
    }



    @RequestMapping(value = "/todo", method = RequestMethod.DELETE)
    public void remove(final Callback<Boolean> callback, final @RequestParam("id") String id) {

        Todo remove = todoMap.remove(id);
        callback.accept(remove!=null);

    }



    @RequestMapping(value = "/todo", method = RequestMethod.GET)
    public void list(final Callback<String> callback, final @RequestParam("body") String body) {
        System.out.println("request: " + body);
        Gson gson = new Gson();
        DataRequest request = gson.fromJson(body, DataRequest.class);
        System.out.printf("Request received, accnr: %s\n", request.getAccountNumber());
        DataReply reply = Util.createJsonReply(request.getAccountNumber(), request.getType(), "123,50");
        String tosend = gson.toJson(reply);
        System.out.println("Sending " + tosend);
        callback.reply(tosend);
    }


}
