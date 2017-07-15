package util;

import api.ApiServiceMain;
import authentication.AuthenticationServiceMain;
import ledger.LedgerServiceMain;
import pin.PinServiceMain;
import transactionin.TransactionReceiveServiceMain;
import transactionout.TransactionDispatchServiceMain;
import ui.UIServiceMain;
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
        LedgerServiceMain.main();
        UsersServiceMain.main();
        UIServiceMain.main();
        AuthenticationServiceMain.main();
        PinServiceMain.main();
        TransactionDispatchServiceMain.main();
        TransactionReceiveServiceMain.main();
        ApiServiceMain.main();
        System.out.println("\n\n\n");
    }
}
