package com.expensedash.server;

import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import com.expensedash.server.model.*;

/**
 * ServerMain — ExpenseDash Server
 * Handles login, group management, join requests, expenses, and real-time sync.
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

                // ───────────────────────────────────────────────
                // REQUEST SNAPSHOT
                // ───────────────────────────────────────────────
                if (line.equals("REQUEST_SNAPSHOT")) {
                    if (session.username != null) {
                        sendSnapshot(pw, session.db, session.username);
                    } else {
                        pw.println("SNAPSHOT_ERR|User not logged in");
                    }
                    continue;
                }

                // ───────────────────────────────────────────────
                // REGISTER / LOGIN
                // ───────────────────────────────────────────────
                if (line.startsWith("REGISTER|")) {
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
                        e.printStackTrace();
                    }
                    continue;
                }

                // ───────────────────────────────────────────────
                // GROUP CREATION (auto add creator as member)
                // ───────────────────────────────────────────────
                if (line.startsWith("ADD_GROUP|")) {
                    String[] p = line.split("\\|", 3);
                    String gName = p[1];
                    String gCat = p.length >= 3 ? p[2] : "";
                    try {
                        if (session.db.groupNameExists(gName)) {
                            pw.println("ADD_GROUP_ERR|DUPLICATE");
                        } else {
                            int gid = session.db.addGroup(gName, gCat, session.username);
                            session.db.addMemberValidated(session.username, gid);
                            pw.println("ADD_GROUP_OK|" + gid);
                            sendSnapshot(pw, session.db, session.username);
                        }
                    } catch (Exception ex) {
                        pw.println("ADD_GROUP_ERR|" + ex.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────────────────────
                // SEARCH GROUPS
                // ───────────────────────────────────────────────
                if (line.startsWith("SEARCH_GROUP|")) {
                    String[] p = line.split("\\|", 2);
                    String query = p.length >= 2 ? p[1] : "";
                    try {
                        List<Group> results = session.db.searchGroups(query);
                        pw.println("SEARCH_BEGIN");
                        for (Group g : results) {
                            pw.println("SEARCH_RESULT|" + g.id + "|" + g.name + "|" + g.category);
                        }
                        pw.println("SEARCH_END");
                    } catch (Exception ex) {
                        pw.println("SEARCH_ERR|" + ex.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────────────────────
                // JOIN REQUEST FLOW (manual approval)
                // ───────────────────────────────────────────────
                if (line.startsWith("JOIN_GROUP|")) {
                    int groupId = Integer.parseInt(line.split("\\|")[1]);
                    try {
                        session.db.createJoinRequest(session.username, groupId);
                        pw.println("JOIN_QUEUED|" + groupId);

                        // Notify group creator live
                        String creator = session.db.getGroupCreator(groupId);
                        if (creator != null) {
                            for (ClientSession cs : clients) {
                                if (creator.equals(cs.username)) {
                                    PrintWriter opw = new PrintWriter(new OutputStreamWriter(cs.socket.getOutputStream()), true);
                                    Group g = session.db.getGroupById(groupId);
                                    int reqId = session.db.getLatestJoinRequestId(session.username, groupId);
                                    opw.println("JOIN_REQ|" + reqId + "|" + groupId + "|" + g.name + "|" + session.username);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        pw.println("JOIN_ERR|" + ex.getMessage());
                    }
                    continue;
                }

                if (line.startsWith("APPROVE_JOIN|")) {
                    int reqId = Integer.parseInt(line.split("\\|")[1]);
                    try {
                        var req = session.db.readJoinRequest(reqId);
                        if (req == null || !"PENDING".equals(req.status)) {
                            pw.println("APPROVE_ERR|NOT_FOUND");
                            continue;
                        }
                        String creator = session.db.getGroupCreator(req.groupId);
                        if (!Objects.equals(creator, session.username)) {
                            pw.println("APPROVE_ERR|NOT_CREATOR");
                            continue;
                        }

                        session.db.approveJoinRequest(reqId);
                        session.db.addMemberValidated(req.username, req.groupId);

                        // Notify the joiner
                        for (ClientSession cs : clients) {
                            if (req.username.equals(cs.username)) {
                                PrintWriter opw = new PrintWriter(new OutputStreamWriter(cs.socket.getOutputStream()), true);
                                Group g = session.db.getGroupById(req.groupId);
                                opw.println("JOIN_OK|" + req.groupId + "|" + g.name);
                                sendSnapshot(opw, cs.db, cs.username);
                            }
                        }

                        sendSnapshot(pw, session.db, session.username);
                        pw.println("APPROVE_OK");
                    } catch (Exception ex) {
                        pw.println("APPROVE_ERR|" + ex.getMessage());
                    }
                    continue;
                }

                if (line.startsWith("REJECT_JOIN|")) {
                    int reqId = Integer.parseInt(line.split("\\|")[1]);
                    try {
                        var req = session.db.readJoinRequest(reqId);
                        if (req == null || !"PENDING".equals(req.status)) {
                            pw.println("REJECT_ERR|NOT_FOUND");
                            continue;
                        }
                        String creator = session.db.getGroupCreator(req.groupId);
                        if (!Objects.equals(creator, session.username)) {
                            pw.println("REJECT_ERR|NOT_CREATOR");
                            continue;
                        }

                        session.db.rejectJoinRequest(reqId);

                        // Notify joiner
                        for (ClientSession cs : clients) {
                            if (req.username.equals(cs.username)) {
                                PrintWriter opw = new PrintWriter(new OutputStreamWriter(cs.socket.getOutputStream()), true);
                                Group g = session.db.getGroupById(req.groupId);
                                opw.println("JOIN_REJ|" + req.groupId + "|" + g.name);
                            }
                        }

                        pw.println("REJECT_OK");
                    } catch (Exception ex) {
                        pw.println("REJECT_ERR|" + ex.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────────────────────
                // ADD EXPENSE (server auto-splits equally)
                // ───────────────────────────────────────────────
                if (line.startsWith("ADD_EXPENSE|")) {
                    String[] p = line.split("\\|", 5);
                    int groupId = Integer.parseInt(p[1]);
                    String payer = p[2];
                    double amount = Double.parseDouble(p[3]);
                    String desc = p[4];
                    try {
                        int expId = session.db.addExpense(groupId, payer, amount, desc);
                        var mems = session.db.getMembersForGroup(groupId);
                        int n = mems.size() > 0 ? mems.size() : 1;
                        double per = Math.round((amount / n) * 100.0) / 100.0;
                        double running = 0.0;
                        for (int i = 0; i < mems.size(); i++) {
                            int mid = mems.get(i).id;
                            double part = (i == mems.size() - 1) ? (amount - running) : per;
                            running += part;
                            session.db.addSplit(expId, mid, part);
                        }
                        broadcast("EXPENSE|" + expId + "|" + groupId + "|" + payer + "|" + amount + "|" + desc);
                        for (var sp : session.db.getSplitsForExpense(expId)) {
                            broadcast("SPLIT|" + expId + "|" + sp.memberId + "|" + sp.amount);
                        }
                    } catch (Exception ex) {
                        pw.println("ADD_EXPENSE_ERR|" + ex.getMessage());
                    }
                    continue;
                }

                // ───────────────────────────────────────────────
                // SETTLE GROUP
                // ───────────────────────────────────────────────
                if (line.startsWith("SETTLE|")) {
                    int gid = Integer.parseInt(line.split("\\|")[1]);
                    try {
                        session.db.settleGroup(gid);
                        broadcast("RESET|" + gid);
                    } catch (Exception ignore) {}
                    continue;
                }
            }

        } catch (Exception e) {
            System.out.println("[Server] Client disconnected or error: " + e.getMessage());
        } finally {
            clients.remove(session);
        }
    }

    // ───────────────────────────────────────────────
    // SNAPSHOT: send user-specific data
    // ───────────────────────────────────────────────
    private static void sendSnapshot(PrintWriter pw, Database db, String username) {
        try {
            pw.println("SNAPSHOT_BEGIN");
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
        String username;
        ClientSession(Socket s, Database db) {
            this.socket = s;
            this.db = db;
        }
    }
}
