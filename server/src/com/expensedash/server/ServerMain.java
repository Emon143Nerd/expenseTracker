package com.expensedash.server;

import java.sql.SQLException;
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

                if (line.equals("REQUEST_SNAPSHOT")) {
                    if (session.username != null) {
                        sendSnapshot(pw, session.db, session.username); // ✅ FIX: username now used
                    } else {
                        pw.println("SNAPSHOT_ERR|User not logged in");
                    }
                }

                else if (line.startsWith("ADD_GROUP|")) {
                    String[] p = line.split("\\|", 3);
                    String gName = p[1];
                    String gCat  = p.length >= 3 ? p[2] : "";

                    try {
                        if (session.db.groupNameExists(gName)) {
                            pw.println("ADD_GROUP_ERR|DUPLICATE");
                        } else {
                            int id = session.db.addGroup(gName, gCat);

                            // ✅ Auto-assign creator as a member
                            if (session.username != null)
                                session.db.addMemberValidated(session.username, id);

                            broadcast("GROUP|" + id + "|" + gName + "|" + gCat);
                            pw.println("ADD_GROUP_OK|" + id);
                        }
                    } catch (Exception ex) {
                        pw.println("ADD_GROUP_ERR|" + ex.getMessage());
                    }
                }

                else if (line.startsWith("ADD_MEMBER|")) {
                    String[] p = line.split("\\|", 3);
                    String username = p[1];
                    int groupId = Integer.parseInt(p[2]);
                    try {
                        int newId = session.db.addMemberValidated(username, groupId);
                        if (newId == -1) {
                            pw.println("ADD_MEMBER_DUP");
                        } else {
                            broadcast("MEMBER|" + newId + "|" + username + "|" + groupId);
                            pw.println("ADD_MEMBER_OK|" + newId);
                        }
                    } catch (SQLException ex) {
                        if ("USER_NOT_FOUND".equals(ex.getMessage())) {
                            pw.println("ADD_MEMBER_ERR|USER_NOT_FOUND");
                        } else {
                            pw.println("ADD_MEMBER_ERR|" + ex.getMessage());
                        }
                    } catch (Exception ex) {
                        pw.println("ADD_MEMBER_ERR|" + ex.getMessage());
                    }
                }

                else if (line.startsWith("JOIN_GROUP|")) {
                    String[] p = line.split("\\|", 3);
                    int groupId = Integer.parseInt(p[1]);
                    String username = p[2];
                    try {
                        int newId = session.db.addMemberValidated(username, groupId);
                        if (newId == -1) {
                            pw.println("JOIN_DUP");
                        } else {
                            broadcast("MEMBER|" + newId + "|" + username + "|" + groupId);
                            pw.println("JOIN_OK|" + newId);
                        }
                    } catch (SQLException ex) {
                        if ("USER_NOT_FOUND".equals(ex.getMessage())) {
                            pw.println("JOIN_ERR|USER_NOT_FOUND");
                        } else {
                            pw.println("JOIN_ERR|" + ex.getMessage());
                        }
                    } catch (Exception ex) {
                        pw.println("JOIN_ERR|" + ex.getMessage());
                    }
                }

                else if (line.startsWith("SEARCH_GROUP|")) {
                    String[] p = line.split("\\|", 2);
                    String query = p.length >= 2 ? p[1] : "";
                    try {
                        List<Group> results = session.db.searchGroups(query);
                        pw.println("SEARCH_BEGIN");
                        for (Group g : results) {
                            pw.println("GROUP|" + g.id + "|" + g.name + "|" + g.category);
                        }
                        pw.println("SEARCH_END");
                    } catch (Exception ex) {
                        pw.println("SEARCH_ERR|" + ex.getMessage());
                    }
                }

                else if (line.startsWith("ADD_EXPENSE|")) {
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
                    String[] p = line.split("\\|", 3);
                    try {
                        boolean ok = session.db.validateUser(p[1], p[2]);
                        if (ok) {
                            session.username = p[1]; // ✅ FIX: store logged-in username
                            pw.println("LOGIN_OK");
                            System.out.println("[Server] User logged in: " + session.username);
                            sendSnapshot(pw, session.db, session.username); // ✅ FIX: now valid
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

    // ✅ FIX: Updated sendSnapshot to be non-static friendly
    private static void sendSnapshot(PrintWriter pw, Database db, String username) {
        try {
            pw.println("SNAPSHOT_BEGIN");

            // ✅ Only groups where user is a member or creator
            List<Integer> userGroups = db.getGroupsForUser(username);

            for (int gid : userGroups) {
                var g = db.getGroupById(gid);
                pw.println("GROUP|" + g.id + "|" + g.name + "|" + g.category);
            }

            for (int gid : userGroups) {
                for (var m : db.getMembersForGroup(gid)) {
                    pw.println("MEMBER|" + m.id + "|" + m.name + "|" + gid);
                }
            }

            for (int gid : userGroups) {
                for (var e : db.getExpensesForGroup(gid)) {
                    pw.println("EXPENSE|" + e.id + "|" + gid + "|" + e.payer + "|" + e.amount + "|" + e.desc);
                    for (var sp : db.getSplitsForExpense(e.id)) {
                        pw.println("SPLIT|" + e.id + "|" + sp.memberId + "|" + sp.amount);
                    }
                }
            }

            pw.println("SNAPSHOT_END");
        } catch (Exception e) {
            pw.println("SNAPSHOT_ERR|" + e.getMessage());
        }
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
        String username; // ✅ FIX: added per-client username
        ClientSession(Socket s, Database db) {
            this.socket = s;
            this.db = db;
        }
    }
}
