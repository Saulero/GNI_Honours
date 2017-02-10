package ui;

/**
 * @author Saul
 */
public class DataRequest {

    private String accountNumber;
    private RequestType type;

    public DataRequest(String accountNumber, RequestType type) {
        this.accountNumber = accountNumber;
        this.type = type;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public RequestType getType() {
        return type;
    }

    public void setType(RequestType type) {
        this.type = type;
    }
}
