
package com.expensedash.client;

public class Session {
    private static String currentUser = "You";
    public static String getCurrentUser(){ return currentUser; }
    public static void setCurrentUser(String u){ currentUser = (u==null||u.isBlank()) ? "You" : u; }
}
