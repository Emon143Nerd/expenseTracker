package com.expensedash.client.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class BalanceRow {
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();

    public BalanceRow(String n, String a, String s) { name.set(n); amount.set(a); status.set(s); }
    public StringProperty nameProperty(){return name;}
    public StringProperty amountProperty(){return amount;}
    public StringProperty statusProperty(){return status;}
}
