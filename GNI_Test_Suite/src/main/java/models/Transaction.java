package models;

public class Transaction {
    private String sourceIBAN;
    private String targetIBAN;
    private String  targetname;
    private String date;
    private float amount;
    private String description;

    public Transaction(String sourceIBAN, String targetIBAN, String targetname, String date, float amount, String description) {
        this.sourceIBAN = sourceIBAN;
        this.targetIBAN = targetIBAN;
        this.targetname = targetname;
        this.date = date;
        this.amount = amount;
        this.description = description;
    }


    public String getSourceIBAN() {
        return sourceIBAN;
    }

    public String getTargetIBAN() {
        return targetIBAN;
    }

    public String getTargetname() {
        return targetname;
    }

    public String getDate() {
        return date;
    }

    public float getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }
}
