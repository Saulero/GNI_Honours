package testcomms;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import util.DataReply;
import util.Util;
import util.RequestType;

import java.util.*;


@RequestMapping("/todo-service")
public class TodoService {


    private final Map<String, Todo> todoMap = new TreeMap<>();


    @RequestMapping(value = "/todo", method = RequestMethod.POST)
    public void add(final Callback<Boolean> callback, final Todo todo) {
        todoMap.put(todo.getId(), todo);
        callback.accept(true);
    }



    @RequestMapping(value = "/todo", method = RequestMethod.DELETE)
    public void remove(final Callback<Boolean> callback, final @RequestParam("id") String id) {

        Todo remove = todoMap.remove(id);
        callback.accept(remove!=null);

    }



    @RequestMapping(value = "/todo", method = RequestMethod.GET)
    public void list(final Callback<String> callback) {
        DataReply reply = Util.createJsonReply("NL55INGB098123134", RequestType.BALANCE, "123,50");
        Gson gson = new Gson();
        String tosend = gson.toJson(reply);
        System.out.println("Sending " + tosend);
        callback.reply(tosend);
    }


}
