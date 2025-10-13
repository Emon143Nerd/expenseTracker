package com.expensedash.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.expensedash.server.model.*;

public class ServerMain {
    public static final int PORT = 5055;
    private static final List<ClientSession> clients = new CopyOnWriteArrayList<>();
    private static Database db;

    public static void main(String[] args) throws Exception {
        System.out.println("[Server] Starting on port " + PORT);
        db = new Database("expensedb.sqlite");
        db.init();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Listening on port " + PORT + "...");
            while (true) {
                Socket s = serverSocket.accept();
                System.out.println("[Server] Client connected: " + s.getInetAddress());
                ClientSession session = new ClientSession(s, db);
                clients.add(session);
                new Thread(() -> handleClient(session)).start();
            }
        }
    }

    private static void handleClient(ClientSession session) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(session.socket.getInputStream()));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(session.socket.getOutputStream()), true)) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("ADD_GROUP|")) {
                    String[] p = line.split("\\|", 4);
                    int id = session.db.addGroup(p[1], p[2]);
                    broadcast("GROUP|" + id + "|" + p[1] + "|" + p[2]);
                }
                else if (line.startsWith("ADD_MEMBER|")) {
                    String[] p = line.split("\\|", 4);
                    int id = session.db.addMember(p[1], Integer.parseInt(p[2]));
                    broadcast("MEMBER|" + id + "|" + p[1] + "|" + p[2]);
                }
                else if (line.startsWith("ADD_EXPENSE|")) {
                    // ADD_EXPENSE|groupId|payer|amount|description|memberId:amount,memberId:amount
                    String[] p = line.split("\\|", 6);
                    int groupId = Integer.parseInt(p[1]);
                    String payer = p[2];
                    double amount = Double.parseDouble(p[3]);
                    String desc = p[4];
                    String splits = p[5];
                    int expenseId = session.db.addExpense(groupId, payer, amount, desc);
                    List<String> splitLines = new ArrayList<>();
                    if (!splits.isEmpty()) {
                        for (String pair : splits.split(",")) {
                            String[] kv = pair.split(":");
                            int mid = Integer.parseInt(kv[0]);
                            double a = Double.parseDouble(kv[1]);
                            session.db.addSplit(expenseId, mid, a);
                            splitLines.add("SPLIT|" + expenseId + "|" + mid + "|" + a);
                        }
                    }
                    broadcast("EXPENSE|" + expenseId + "|" + groupId + "|" + payer + "|" + amount + "|" + desc);
                    for (String sp: splitLines) broadcast(sp);
                }
                else if (line.startsWith("REGISTER|")) {
                    // REGISTER|username|passwordHash
                    String[] p = line.split("\\|", 3);
                    try {
                        boolean ok = session.db.registerUser(p[1], p[2]);
                        if (ok) {
                            pw.println("REGISTER_OK");
                            System.out.println("[Server] New user registered: " + p[1]);
                        } else {
                            pw.println("REGISTER_DUP");
                        }
                    } catch (Exception e) {
                        pw.println("REGISTER_ERR|" + e.getMessage());
                        e.printStackTrace();
                    }
                }
                else if (line.startsWith("LOGIN|")) {
                    // LOGIN|username|passwordHash
                    String[] p = line.split("\\|", 3);
                    try {
                        boolean ok = session.db.validateUser(p[1], p[2]);
                        if (ok) {
                            pw.println("LOGIN_OK");
                            System.out.println("[Server] User logged in: " + p[1]);
                            // ✅ Send snapshot AFTER successful login
                            sendSnapshot(pw, session.db);
                        } else {
                            pw.println("LOGIN_FAIL");
                        }
                    } catch (Exception e) {
                        pw.println("LOGIN_ERR|" + e.getMessage());
                        e.printStackTrace();
                    }
                }
                else if (line.startsWith("SETTLE|")) {
                    String[] p = line.split("\\|", 2);
                    int groupId = Integer.parseInt(p[1]);
                    try {
                        session.db.settleGroup(groupId);
                        broadcast("RESET|" + groupId);
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception e) {
            System.out.println("[Server] Client disconnected or error: " + e.getMessage());
        } finally {
            clients.remove(session);
        }
    }

    private static void sendSnapshot(PrintWriter pw, Database db) throws Exception {
        pw.println("SNAPSHOT_BEGIN");
        for (Group g : db.getGroups()) {
            pw.println("GROUP|" + g.id + "|" + g.name + "|" + g.category);
        }
        for (Member m : db.getMembers()) {
            pw.println("MEMBER|" + m.id + "|" + m.name + "|" + m.groupId);
        }
        for (Expense e : db.getExpenses()) {
            pw.println("EXPENSE|" + e.id + "|" + e.groupId + "|" + e.payer + "|" + e.amount + "|" + e.description);
        }
        for (Split sp : db.getSplits()) {
            pw.println("SPLIT|" + sp.expenseId + "|" + sp.memberId + "|" + sp.amount);
        }
        pw.println("SNAPSHOT_END");
    }

    private static void broadcast(String msg) {
        for (ClientSession c : clients) {
            try {
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(c.socket.getOutputStream()), true);
                pw.println(msg);
            } catch (Exception ignored) {}
        }
    }

    private static class ClientSession {
        Socket socket;
        Database db;
        ClientSession(Socket s, Database db) {
            this.socket = s;
            this.db = db;
        }
    }
}
