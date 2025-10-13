package com.expensedash.server.model;
public class Expense { public final int id; public final int groupId; public final String payer; public final double amount; public final String description;
    public Expense(int id,int groupId,String payer,double amount,String desc){this.id=id;this.groupId=groupId;this.payer=payer;this.amount=amount;this.description=desc;}
}