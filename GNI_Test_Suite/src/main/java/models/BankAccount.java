package models;

import java.util.*;

public class BankAccount {

    private String iBAN;
    private float balance;
    private Set<CustomerAccount> accessReceivers;
    private List<Transaction> transactions = new LinkedList<Transaction>();



    public BankAccount(String iBAN) {
        this.iBAN = iBAN;
        this.accessReceivers = new HashSet<CustomerAccount>();
        this.balance = 0;
    }

    public String getiBAN() {
        return iBAN;
    }

    public Set<CustomerAccount> getAccessReceivers() {
        return accessReceivers;
    }

    public void addAccessReceiver(CustomerAccount customer){
        this.accessReceivers.add(customer);
    }

    public float getBalance() {
        return balance;
    }

    public void setBalance(float balance) {
        this.balance = balance;
    }

    public void increaseBalance(float balance) {
        this.balance += balance;
    }

    public void lowerBalance(float balance) {
        this.balance -= balance;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void addTransaction(Transaction transactions) {
        this.transactions.add(transactions);
    }

    public void processDeposit(String iBAN, float amount){
        increaseBalance(amount);
        addTransaction(new Transaction("ATM", iBAN, "", new Date().toString(), amount, "Deposit"));
    }

    public void processCardPayment(String sIBAN, String tIBAN, float amount){
        lowerBalance(amount);
        addTransaction(new Transaction(sIBAN, tIBAN, "", new Date().toString(), amount, "Card Payment"));
    }

    public void processTransfer(String sIBAN, String tIBAN, String targetName, float amount, String description){
        if(this.getiBAN().equals(sIBAN)){
            lowerBalance(amount);
        } else {
            increaseBalance(amount);
        }

        addTransaction(new Transaction(sIBAN, tIBAN, targetName, new Date().toString(), amount, description));
    }





}
