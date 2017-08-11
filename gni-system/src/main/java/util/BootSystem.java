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
 * @version 2
 */
public class BootSystem {

    public static void main(String[] args) {
        startServices();
    }

    private static void startServices() {
        TableCreator.truncateTables();
        String systemInformationServicePort = "" + PortScanner.getAvailablePort();
        String systemInformationServiceHost = "localhost";
        String[] args = {systemInformationServicePort, systemInformationServiceHost};
        
        SystemInformationServiceMain.main(args);
        LedgerServiceMain.main(args);
        UsersServiceMain.main(args);
        AuthenticationServiceMain.main(args);
        PinServiceMain.main(args);
        TransactionDispatchServiceMain.main(args);
        TransactionReceiveServiceMain.main(args);
        ApiServiceMain.main(args);
    }
}
