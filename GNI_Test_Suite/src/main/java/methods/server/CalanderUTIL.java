package methods.server;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class CalanderUTIL {

    public static String CalanderToDateString(Calendar calendar){
        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
        return format1.format(calendar.getTime());
    }
}
