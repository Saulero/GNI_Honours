package systeminformation;

import com.google.gson.Gson;
import io.advantageous.qbit.annotation.RequestMapping;
import io.advantageous.qbit.annotation.RequestMethod;
import io.advantageous.qbit.annotation.RequestParam;
import io.advantageous.qbit.reactive.Callback;
import util.JSONParser;

import java.time.LocalDate;

/**
 * @author Noel
 * @version 1
 * Service that handles all system-wide information.
 * Currently tracks the system date.
 */
@RequestMapping("/systemInfo")
class SystemInformationService {
    /** Current date of the system, used for tracking transactions and validating pin cards. */
    private LocalDate systemDate;
    /** Used for json conversions. */
    private Gson jsonConverter;
    /** Prefix used when printing to indicate the message is coming from the SystemInformation Service. */
    private static final String PREFIX = "[SYSINFO]             :";

    /**
     * Constructor to start the service. This will set the systemDate to the date of the day the method is ran.
     */
    SystemInformationService() {
        this.systemDate = LocalDate.now();
        this.jsonConverter = new Gson();
        System.out.printf("%s Set date to %s", PREFIX, this.systemDate.toString());
    }

    /**
     * Increments the systemDate by the amount of days supplied by the requester.
     * @param callback Used to send the result of the request back to the requester.
     * @param days Amount of days to increment the systemDate with.
     */
    @RequestMapping(value = "/date/increment", method = RequestMethod.PUT)
    void incrementDate(final Callback<String> callback, final @RequestParam("days") Long days) {
        this.systemDate = this.systemDate.plusDays(days);
        System.out.printf("%s Added %d days to system date, new date is %s\n", PREFIX, days,
                this.systemDate.toString());
        callback.reply(jsonConverter.toJson(JSONParser.createMessageWrapper(false, 200,
                "Normal Reply")));
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
}
