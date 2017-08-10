package systeminformation;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import util.JSONParser;
import util.TableCreator;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * @author Noel
 * @version 1
 * Service that handles all system-wide information.
 * Currently tracks the system date.
 */
@RequestMapping("/systemInfo")
class SystemInformationService {
    /** Calendar of the system, used for tracking transactions and validating pin cards. */
    private Calendar myCal;
    /** LocalDat with current date */
    private LocalDate systemDate;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the SystemInformation Service. */
    private static final String PREFIX = "[SYSINFO]             :";

    /**
     * Constructor to start the service. This will set the systemDate to the date of the day the method is ran.
     */
    SystemInformationService() {
        systemDate = LocalDate.now();
        syncCalendar();
        this.jsonConverter = new Gson();
        System.out.printf("%s Set date to %s\n", PREFIX, systemDate.toString());
    }

    /**
     * Increments the systemDate by the amount of days supplied by the requester.
     * @param callback Used to send the result of the request back to the requester.
     * @param days Amount of days to increment the systemDate with.
     */
    @RequestMapping(value = "/date/increment", method = RequestMethod.PUT)
    void incrementDate(final Callback<String> callback, final @RequestParam("days") long days) {
        processPassingTime(days);
        System.out.printf("%s Added %d days to system date, new date is %s\n", PREFIX, days,
                this.systemDate.toString());
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200, "Normal Reply")));
    }

    private void processPassingTime(final long days) {
        System.out.println(systemDate.toString());
        int daysInMonth = myCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dayOfTheMonth = systemDate.getDayOfMonth();
        this.systemDate = this.systemDate.plusDays(((daysInMonth - dayOfTheMonth) + 1));
        syncCalendar();
        if (days >= ((daysInMonth - dayOfTheMonth) + 1)) {
            // call to ledger
            System.out.println("processed " + ((daysInMonth - dayOfTheMonth) + 1) + " days");
            processPassingTime(days - ((daysInMonth - dayOfTheMonth) + 1));
        } else {
            System.out.println(days + " leftover");
        }
    }

    private void syncCalendar() {
        myCal = new GregorianCalendar(
                systemDate.getYear(),
                systemDate.getMonth().getValue() - 1,
                systemDate.getDayOfMonth());
    }

    /**
     * Get method for the systemDate.
     * @param callback Used to send the systemDate back to the requester.
     */
    @RequestMapping(value = "/date", method = RequestMethod.GET)
    void getDate(final Callback<String> callback) {
        System.out.printf("%s received date request, sending callback.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply", this.systemDate)));
    }

    /**
     * Reset method for the systemDate & Database.
     * @param callback Used to send the result of the request back to the requester.
     */
    @RequestMapping(value = "/reset", method = RequestMethod.POST)
    void reset(final Callback<String> callback) {
        TableCreator.truncateTables();
        this.systemDate = LocalDate.now();
        System.out.printf("%s Reset request successful, sending callback.\n", PREFIX);
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply", this.systemDate)));
    }
}
