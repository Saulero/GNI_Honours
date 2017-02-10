package ui;

/**
 * @author Saul
 */
public class DataReply {

    private String accountNumber;
    private RequestType type;
    private String data;

    public DataReply(String accountNumber, RequestType type, String data) {
        this.accountNumber = accountNumber;
        this.type = type;
        this.data = data;
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

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
