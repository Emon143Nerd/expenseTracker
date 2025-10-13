package com.expensedash.server.model;
public class Split { public final int expenseId; public final int memberId; public final double amount;
    public Split(int expenseId,int memberId,double amount){this.expenseId=expenseId;this.memberId=memberId;this.amount=amount;}
}