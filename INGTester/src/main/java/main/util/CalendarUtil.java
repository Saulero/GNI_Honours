package main.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class CalendarUtil {

    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Returns a Calendar object set to the time described by the string in the format yyyy-MM-dd
     *
     * @param string containing the date in format yyyy-MM-dd
     * @return calendar object
     */
    public static Calendar getCalenderOfString(String string) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(sdf.parse(string));
            return calendar;
        } catch (ParseException e) {
            throw new IllegalStateException("Could not parse String: " + string);
        }
    }

    /**
     * Returns the amount of days needed to simulate in order to get to the first of the next month
     *
     * @param calendar of the current day
     * @return amount of days to simulate
     */
    public static int getDaysTillNextFirstOfMonth(Calendar calendar) {
        return calendar.getMaximum(Calendar.DAY_OF_MONTH) - calendar.get(Calendar.DAY_OF_MONTH) + 1;
    }

}
