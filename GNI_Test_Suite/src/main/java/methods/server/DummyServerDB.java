package methods.server;

import models.CustomerAccount;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class DummyServerDB {

    private static DummyServerDB singleton = new DummyServerDB( );

    /* A private Constructor prevents any other
  * class from instantiating.
  */
    private DummyServerDB() {
        customers = new HashSet<CustomerAccount>();
        calendar = Calendar.getInstance();
    }

    /* Static 'instance' method */
    public static DummyServerDB getInstance( ) {
        return singleton;
    }


    //-----------------------------------------//

    private Set<CustomerAccount> customers;

    private Calendar calendar;

    public Set<CustomerAccount> getCustomers() {
        return customers;
    }

    public void setCustomers(Set<CustomerAccount> customers) {
        this.customers = customers;
    }

    public void addCustomer(CustomerAccount account){
        customers.add(account);
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public Calendar getExpirationCalendar() {
        Calendar cal = (Calendar) this.calendar.clone();
        cal.add(Calendar.YEAR, 4);
        return cal;
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public void addDay(){
        this.calendar.add(Calendar.DATE, 1);
    }
}
