package com.expensedash.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import com.expensedash.server.model.*;

/**
 * ✅ FINAL VERSION — ExpenseDash Server
 * - Direct group joining (no requests)
 * - Creator auto-added to group
 * - Real-time expense and membership updates
 * - Simplified, stable snapshot sync
 */
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
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(socket, db);
                clients.add(session);
                System.out.println("[Server] Client connected: " + socket.getInetAddress());
                new Thread(() -> handleClient(session)).start();
            }
        }
    }

    private static void handleClient(ClientSession session) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(session.socket.getInputStream()));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(session.socket.getOutputStream()), true)) {

            String line;
            while ((line = br.readLine()) != null) {

                // ───────────────────────────────
                // SNAPSHOT REQUEST
                // ───────────────────────────────
                if (line.equals("REQUEST_SNAPSHOT")) {
                    if (session.username != null) sendSnapshot(pw, session.db, session.username);
                    else pw.println("SNAPSHOT_ERR|User not logged in");
                    continue;
                }

                // ───────────────────────────────
                // REGISTER / LOGIN
                // ───────────────────────────────
                if (line.startsWith("REGISTER|")) {
                    String[] p = line.split("\\|", 3);
                    try {
                        boolean ok = session.db.registerUser(p[1], p[2]);
                        pw.println(ok ? "REGISTER_OK" : "REGISTER_DUP");
                    } catch (Exception e) {
                        pw.println("REGISTER_ERR|" + e.getMessage());
                    }
                    continue;
                }

                if (line.startsWith("LOGIN|")) {
                    String[] p = line.split("\\|", 3);
                    try {
                        boolean ok = session.db.validateUser(p[1], p[2]);
                        if (ok) {
                            session.username = p[1];
                            pw.println("LOGIN_OK");
                            System.out.println("[Server] User logged in: " + session.username);
                            sendSnapshot(pw, session.db, session.username);
                        } else {
                            pw.println("LOGIN_FAIL");
                        }
                    } catch (Exception e) {
                        pw.println("LOGIN_ERR|" + e.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────
                // CREATE GROUP (auto-member)
                // ───────────────────────────────
                if (line.startsWith("ADD_GROUP|")) {
                    String[] p = line.split("\\|", 3);
                    String name = p[1];
                    String category = p.length >= 3 ? p[2] : "";
                    try {
                        if (session.db.groupNameExists(name)) {
                            pw.println("ADD_GROUP_ERR|DUPLICATE");
                        } else {
                            int gid = session.db.addGroup(name, category, session.username);
                            session.db.addMemberValidated(session.username, gid);

                            // Notify all clients
                            broadcast("GROUP|" + gid + "|" + name + "|" + category);
                            pw.println("ADD_GROUP_OK|" + gid);
                            sendSnapshot(pw, session.db, session.username);
                        }
                    } catch (Exception e) {
                        pw.println("ADD_GROUP_ERR|" + e.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────
                // ───────────────────────────────
// SEARCH GROUPS (global search)
// ───────────────────────────────
                if (line.startsWith("SEARCH_GROUP|")) {
                    String[] p = line.split("\\|", 2);
                    String query = p.length >= 2 ? p[1] : "";
                    try {
                        List<Group> results = session.db.searchGroups(query); // ✅ searches all groups globally
                        pw.println("SEARCH_BEGIN");
                        for (Group g : results) {
                            pw.println("SEARCH_RESULT|" + g.id + "|" + g.name + "|" + g.category);
                        }
                        pw.println("SEARCH_END");
                    } catch (Exception e) {
                        pw.println("SEARCH_ERR|" + e.getMessage());
                    }
                    continue;
                }


                // ───────────────────────────────
                // JOIN GROUP (instant join)
                // ───────────────────────────────
                if (line.startsWith("JOIN_GROUP|")) {
                    int groupId = Integer.parseInt(line.split("\\|")[1]);
                    try {
                        if (!session.db.isMemberInGroup(session.username, groupId)) {
                            session.db.addMemberValidated(session.username, groupId);
                        }
                        Group g = session.db.getGroupById(groupId);

                        broadcast("MEMBER|" + session.username + "|" + groupId);
                        pw.println("JOIN_OK|" + groupId + "|" + g.name);
                        sendSnapshot(pw, session.db, session.username);
                    } catch (Exception e) {
                        pw.println("JOIN_ERR|" + e.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────
                // ADD EXPENSE (split equally)
                // ───────────────────────────────
                if (line.startsWith("ADD_EXPENSE|")) {
                    String[] p = line.split("\\|", 5);
                    int groupId = Integer.parseInt(p[1]);
                    String payer = p[2];
                    double amount = Double.parseDouble(p[3]);
                    String desc = p[4];
                    try {
                        int expId = session.db.addExpense(groupId, payer, amount, desc);
                        var members = session.db.getMembersForGroup(groupId);
                        int n = members.isEmpty() ? 1 : members.size();
                        double per = Math.round((amount / n) * 100.0) / 100.0;

                        for (var m : members) {
                            session.db.addSplit(expId, m.id, per);
                        }

                        // Broadcast new expense to all clients
                        broadcast("EXPENSE|" + expId + "|" + groupId + "|" + payer + "|" + amount + "|" + desc);
                        for (var sp : session.db.getSplitsForExpense(expId)) {
                            broadcast("SPLIT|" + expId + "|" + sp.memberId + "|" + sp.amount);
                        }
                    } catch (Exception e) {
                        pw.println("ADD_EXPENSE_ERR|" + e.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────
                // SETTLE GROUP
                // ───────────────────────────────
                if (line.startsWith("SETTLE|")) {
                    int gid = Integer.parseInt(line.split("\\|")[1]);
                    try {
                        session.db.settleGroup(gid);
                        broadcast("RESET|" + gid);
                    } catch (Exception e) {
                        pw.println("SETTLE_ERR|" + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("[Server] Client disconnected: " + e.getMessage());
        } finally {
            clients.remove(session);
        }
    }

    // ───────────────────────────────
    // SEND USER SNAPSHOT
    // ───────────────────────────────
    private static void sendSnapshot(PrintWriter pw, Database db, String username) {
        try {
            pw.println("SNAPSHOT_BEGIN");
            List<Integer> groupIds = db.getGroupsForUser(username);

            for (int gid : groupIds) {
                Group g = db.getGroupById(gid);
                pw.println("GROUP|" + g.id + "|" + g.name + "|" + g.category);

                for (var m : db.getMembersForGroup(gid))
                    pw.println("MEMBER|" + m.id + "|" + m.name + "|" + gid);

                for (var e : db.getExpensesForGroup(gid)) {
                    pw.println("EXPENSE|" + e.id + "|" + gid + "|" + e.payer + "|" + e.amount + "|" + e.desc);
                    for (var sp : db.getSplitsForExpense(e.id))
                        pw.println("SPLIT|" + e.id + "|" + sp.memberId + "|" + sp.amount);
                }
            }

            pw.println("SNAPSHOT_END");
        } catch (Exception e) {
            pw.println("SNAPSHOT_ERR|" + e.getMessage());
        }
    }

    // ───────────────────────────────
    // BROADCAST TO ALL CLIENTS
    // ───────────────────────────────
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
        String username;

        ClientSession(Socket socket, Database db) {
            this.socket = socket;
            this.db = db;
        }
    }
}
