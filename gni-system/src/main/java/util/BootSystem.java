package util;

import api.ApiServiceMain;
import authentication.AuthenticationServiceMain;
import ledger.LedgerServiceMain;
import pin.PinServiceMain;
import systeminformation.SystemInformationServiceMain;
import transactionin.TransactionReceiveServiceMain;
import transactionout.TransactionDispatchServiceMain;
import users.UsersServiceMain;

/**
 * Boots the entire system, i.e. starts all individual services.
 * @author Saul
 * @version 1
 */
public class BootSystem {

    public static void main(String[] args) {
        startServices();
    }

    public static void startServices() {
        TableCreator.truncateTables();
        SystemInformationServiceMain.main(null);
        LedgerServiceMain.main(null);
        UsersServiceMain.main(null);
        AuthenticationServiceMain.main(null);
        PinServiceMain.main(null);
        TransactionDispatchServiceMain.main(null);
        TransactionReceiveServiceMain.main(null);
        ApiServiceMain.main(null);
        System.out.println("\n\n\n");
    }
}
