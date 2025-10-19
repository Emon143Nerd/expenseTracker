package com.expensedash.server;

import java.sql.*;
import java.util.*;
import com.expensedash.server.model.*;

public class Database {
    private final String dbPath;

    public Database(String path) {
        this.dbPath = path;
    }

    private Connection connect() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        return DriverManager.getConnection(url);
    }

    public void init() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS groups(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL UNIQUE, " +
                    "category TEXT, " +
                    "creator TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS members(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "group_id INTEGER NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS expenses(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "group_id INTEGER NOT NULL, " +
                    "payer TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "description TEXT, " +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP)");

            st.execute("CREATE TABLE IF NOT EXISTS splits(" +
                    "expense_id INTEGER NOT NULL, " +
                    "member_id INTEGER NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "PRIMARY KEY(expense_id, member_id))");

            st.execute("CREATE TABLE IF NOT EXISTS users(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT NOT NULL)");

            st.execute("CREATE TABLE IF NOT EXISTS join_requests (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT NOT NULL, " +
                    "group_id INTEGER NOT NULL, " +
                    "status TEXT CHECK(status IN ('PENDING','APPROVED','REJECTED')) DEFAULT 'PENDING')");

            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_name ON groups(name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_members_group ON members(group_id)");
        }
        seed();
    }

    private void seed() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM groups")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    st.execute("INSERT INTO groups(name, category, creator) VALUES " +
                            "('Roommates','Living','admin'),('Trip to Europe','Travel','admin'),('Office Lunch','Work','admin'),('Birthday Party','Event','admin')");
                    st.execute("INSERT INTO members(name, group_id) VALUES " +
                            "('Alice Johnson',1),('Bob Smith',1),('Carol Davis',1),('You',1)");
                    st.execute("INSERT INTO expenses(group_id,payer,amount,description) VALUES " +
                            "(1,'You',45.50,'Groceries'),(1,'Alice Johnson',23.75,'Utilities'),(1,'You',67.20,'Internet')");
                    st.execute("INSERT INTO splits(expense_id,member_id,amount) VALUES " +
                            "(1,1,11.375),(1,2,11.375),(1,3,11.375),(1,4,11.375)," +
                            "(2,1,5.94),(2,2,5.94),(2,3,5.94),(2,4,5.94)," +
                            "(3,1,16.8),(3,2,16.8),(3,3,16.8),(3,4,16.8)");
                }
            }
            c.commit();
        }
    }

    // --------------------------------------------------------------------
    // GROUPS
    // --------------------------------------------------------------------
    public List<Group> getGroups() throws SQLException {
        List<Group> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT id,name,category FROM groups")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Group(rs.getInt(1), rs.getString(2), rs.getString(3)));
            }
        }
        return list;
    }

    public int addGroup(String name, String category, String creator) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO groups(name,category,creator) VALUES (?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, creator);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public Group getGroupById(int id) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, category FROM groups WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Group(rs.getInt(1), rs.getString(2), rs.getString(3));
            } else {
                throw new SQLException("Group not found: " + id);
            }
        }
    }

    public boolean groupNameExists(String name) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM groups WHERE name=? LIMIT 1")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public String getGroupCreator(int groupId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT creator FROM groups WHERE id=?")) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        }
    }

    public List<Group> searchGroups(String query) throws SQLException {
        List<Group> list = new ArrayList<>();
        String q = (query == null || query.isBlank()) ? "%" : "%" + query.toLowerCase() + "%";
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,name,category FROM groups WHERE LOWER(name) LIKE ? ORDER BY name ASC")) {
            ps.setString(1, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Group(rs.getInt(1), rs.getString(2), rs.getString(3)));
            }
        }
        return list;
    }

    // --------------------------------------------------------------------
    // JOIN REQUESTS
    // --------------------------------------------------------------------
//    public void createJoinRequest(String username, int groupId) throws SQLException {
//        try (Connection c = connect()) {
//            try (PreparedStatement check = c.prepareStatement(
//                    "SELECT 1 FROM join_requests WHERE username=? AND group_id=? AND status='PENDING'")) {
//                check.setString(1, username);
//                check.setInt(2, groupId);
//                ResultSet rs = check.executeQuery();
//                if (rs.next()) return;
//            }
//            try (PreparedStatement ps = c.prepareStatement(
//                    "INSERT INTO join_requests(username, group_id, status) VALUES (?, ?, 'PENDING')")) {
//                ps.setString(1, username);
//                ps.setInt(2, groupId);
//                ps.executeUpdate();
//            }
//        }
//    }

//    public static class JoinReq {
//        public final int id, groupId;
//        public final String username, status;
//        public JoinReq(int id, int groupId, String username, String status) {
//            this.id = id;
//            this.groupId = groupId;
//            this.username = username;
//            this.status = status;
//        }
//    }

//    public JoinReq readJoinRequest(int id) throws SQLException {
//        try (Connection c = connect();
//             PreparedStatement ps = c.prepareStatement(
//                     "SELECT id, group_id, username, status FROM join_requests WHERE id=?")) {
//            ps.setInt(1, id);
//            ResultSet rs = ps.executeQuery();
//            if (rs.next()) {
//                return new JoinReq(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4));
//            }
//            return null;
//        }
//    }
//
//    public void approveJoinRequest(int id) throws SQLException {
//        try (Connection c = connect();
//             PreparedStatement ps = c.prepareStatement("UPDATE join_requests SET status='APPROVED' WHERE id=?")) {
//            ps.setInt(1, id);
//            ps.executeUpdate();
//        }
//    }
//
//
//
//    public int getLatestJoinRequestId(String username, int groupId) throws SQLException {
//        try (Connection c = connect();
//             PreparedStatement ps = c.prepareStatement(
//                     "SELECT id FROM join_requests WHERE username=? AND group_id=? ORDER BY id DESC LIMIT 1")) {
//            ps.setString(1, username);
//            ps.setInt(2, groupId);
//            ResultSet rs = ps.executeQuery();
//            return rs.next() ? rs.getInt(1) : -1;
//        }
//    }

    // --------------------------------------------------------------------
    // MEMBERS
    // --------------------------------------------------------------------
    public List<Member> getMembers() throws SQLException {
        List<Member> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT id,name,group_id FROM members")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Member(rs.getInt(1), rs.getString(2), rs.getInt(3)));
            }
        }
        return list;
    }

    public int addMember(String name, int groupId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO members(name,group_id) VALUES (?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, groupId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public boolean isMemberInGroup(String username, int groupId) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM members WHERE name=? AND group_id=? LIMIT 1")) {
            ps.setString(1, username);
            ps.setInt(2, groupId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public int addMemberValidated(String username, int groupId) throws SQLException {
        if (!userExists(username)) {
            throw new SQLException("USER_NOT_FOUND");
        }
        if (isMemberInGroup(username, groupId)) {
            return -1;
        }
        return addMember(username, groupId);
    }

    public List<Member> getMembersForGroup(int gid) throws SQLException {
        List<Member> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,name,group_id FROM members WHERE group_id=?")) {
            ps.setInt(1, gid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Member(rs.getInt(1), rs.getString(2), rs.getInt(3)));
            }
        }
        return list;
    }

    // --------------------------------------------------------------------
    // USERS
    // --------------------------------------------------------------------
    public boolean registerUser(String username, String passwordHash) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(username,password_hash) VALUES (?,?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean validateUser(String username, String passwordHash) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE username=? AND password_hash=?")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public boolean userExists(String username) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM users WHERE username=? LIMIT 1")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public List<Integer> getGroupsForUser(String username) throws SQLException {
        List<Integer> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT DISTINCT group_id FROM members WHERE name=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getInt(1));
        }
        return list;
    }

    // --------------------------------------------------------------------
    // EXPENSES + SPLITS
    // --------------------------------------------------------------------
    public int addExpense(int groupId, String payer, double amount, String desc) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO expenses(group_id,payer,amount,description) VALUES (?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, groupId);
            ps.setString(2, payer);
            ps.setDouble(3, amount);
            ps.setString(4, desc);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public List<Expense> getExpensesForGroup(int gid) throws SQLException {
        List<Expense> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,group_id,payer,amount,description FROM expenses WHERE group_id=?")) {
            ps.setInt(1, gid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Expense(
                        rs.getInt(1),
                        rs.getInt(2),
                        rs.getString(3),
                        rs.getDouble(4),
                        rs.getString(5)
                ));
            }
        }
        return list;
    }

    public void addSplit(int expenseId, int memberId, double amount) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO splits(expense_id,member_id,amount) VALUES (?,?,?)")) {
            ps.setInt(1, expenseId);
            ps.setInt(2, memberId);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        }
    }

    public List<Split> getSplitsForExpense(int eid) throws SQLException {
        List<Split> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT expense_id,member_id,amount FROM splits WHERE expense_id=?")) {
            ps.setInt(1, eid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Split(rs.getInt(1), rs.getInt(2), rs.getDouble(3)));
            }
        }
        return list;
    }

    public void settleGroup(int groupId) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement(
                    "DELETE FROM splits WHERE expense_id IN (SELECT id FROM expenses WHERE group_id=?)");
                 PreparedStatement ps2 = c.prepareStatement(
                         "DELETE FROM expenses WHERE group_id=?")) {
                ps1.setInt(1, groupId);
                ps1.executeUpdate();
                ps2.setInt(1, groupId);
                ps2.executeUpdate();
            }
            c.commit();
        }
    }
}
