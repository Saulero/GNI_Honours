package methods.server;

import models.CustomerAccount;

import java.util.HashSet;
import java.util.Set;

public class DummyServerDB {

    private static DummyServerDB singleton = new DummyServerDB( );

    /* A private Constructor prevents any other
  * class from instantiating.
  */
    private DummyServerDB() {
        customers = new HashSet<CustomerAccount>();
    }

    /* Static 'instance' method */
    public static DummyServerDB getInstance( ) {
        return singleton;
    }

    //-----------------------------------------//

    private Set<CustomerAccount> customers;

    public Set<CustomerAccount> getCustomers() {
        return customers;
    }

    public void setCustomers(Set<CustomerAccount> customers) {
        this.customers = customers;
    }

    public void addCustomer(CustomerAccount account){
        customers.add(account);
    }
}
