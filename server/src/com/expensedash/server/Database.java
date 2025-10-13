package com.expensedash.server;

import java.sql.*;
import java.util.*;
import com.expensedash.server.model.*;

public class Database {
    private final String dbPath;
    public Database(String path) { this.dbPath = path; }

    private Connection connect() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        return DriverManager.getConnection(url);
    }

    public void init() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS groups(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS members(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, group_id INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL, payer TEXT NOT NULL, amount REAL NOT NULL, description TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            st.execute("CREATE TABLE IF NOT EXISTS splits(expense_id INTEGER NOT NULL, member_id INTEGER NOT NULL, amount REAL NOT NULL, PRIMARY KEY(expense_id, member_id))");
            st.execute("CREATE TABLE IF NOT EXISTS users(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT UNIQUE NOT NULL," +
                    "password_hash TEXT NOT NULL)");
        }
        seed();
    }

    private void seed() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM groups")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    st.execute("INSERT INTO groups(name, category) VALUES ('Roommates','Living'),('Trip to Europe','Travel'),('Office Lunch','Work'),('Birthday Party','Event')");
                    st.execute("INSERT INTO members(name, group_id) VALUES ('Alice Johnson',1),('Bob Smith',1),('Carol Davis',1),('You',1)");
                    st.execute("INSERT INTO expenses(group_id,payer,amount,description) VALUES (1,'You',45.50,'Groceries'),(1,'Alice Johnson',23.75,'Utilities'),(1,'You',67.20,'Internet')");
                    st.execute("INSERT INTO splits(expense_id,member_id,amount) VALUES (1,1,11.375),(1,2,11.375),(1,3,11.375),(1,4,11.375),(2,1,5.94),(2,2,5.94),(2,3,5.94),(2,4,5.94),(3,1,16.8),(3,2,16.8),(3,3,16.8),(3,4,16.8)");
                }
            }
            c.commit();
        }
    }

    public List<Group> getGroups() throws SQLException {
        List<Group> list = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT id,name,category FROM groups")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Group(rs.getInt(1), rs.getString(2), rs.getString(3)));
        }
        return list;
    }

    public int addGroup(String name, String category) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("INSERT INTO groups(name,category) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public List<Member> getMembers() throws SQLException {
        List<Member> list = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT id,name,group_id FROM members")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Member(rs.getInt(1), rs.getString(2), rs.getInt(3)));
        }
        return list;
    }

    public int addMember(String name, int groupId) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("INSERT INTO members(name,group_id) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, groupId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public List<Expense> getExpenses() throws SQLException {
        List<Expense> list = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT id,group_id,payer,amount,description FROM expenses")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Expense(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getDouble(4), rs.getString(5)));
        }
        return list;
    }

    public int addExpense(int groupId, String payer, double amount, String desc) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("INSERT INTO expenses(group_id,payer,amount,description) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, groupId);
            ps.setString(2, payer);
            ps.setDouble(3, amount);
            ps.setString(4, desc);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    public List<Split> getSplits() throws SQLException {
        List<Split> list = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT expense_id,member_id,amount FROM splits")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Split(rs.getInt(1), rs.getInt(2), rs.getDouble(3)));
        }
        return list;
    }

    public void addSplit(int expenseId, int memberId, double amount) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("INSERT INTO splits(expense_id,member_id,amount) VALUES (?,?,?)")) {
            ps.setInt(1, expenseId);
            ps.setInt(2, memberId);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        }
    }

    public boolean registerUser(String username, String passwordHash) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(username,password_hash) VALUES (?,?)")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false; // username taken
        }
    }

    public boolean validateUser(String username, String passwordHash) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE username=? AND password_hash=?")) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }


    public void settleGroup(int groupId) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM splits WHERE expense_id IN (SELECT id FROM expenses WHERE group_id=?)");
                 PreparedStatement ps2 = c.prepareStatement("DELETE FROM expenses WHERE group_id=?")) {
                ps1.setInt(1, groupId);
                ps1.executeUpdate();
                ps2.setInt(1, groupId);
                ps2.executeUpdate();
            }
            c.commit();
        }
    }
}  // ✅ <-- this is now the *only* closing brace at the very end
