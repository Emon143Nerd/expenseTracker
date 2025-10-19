package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import com.expensedash.client.net.NetClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.*;

public class DashboardController implements Initializable {

    // --- UI Elements (FXML bindings) ---
    @FXML private Label welcomeLabel;
    @FXML private TextField groupSearch;
    @FXML private ListView<String> groupList;
    @FXML private Label totalPaid;
    @FXML private Label totalOwed;
    @FXML private Label totalReceivable;
    @FXML private TableView<BalanceRow> balancesTable;
    @FXML private TableColumn<BalanceRow, String> colName;
    @FXML private TableColumn<BalanceRow, String> colAmount;
    @FXML private TableColumn<BalanceRow, String> colStatus;
    @FXML private PieChart pieChart;

    // New fields for Add Expense section
    @FXML private TextField expenseAmount;
    @FXML private TextField expenseDescription;

    private NetClient net;

    // --- Data Structures ---
    private final Map<Integer, String> members = new HashMap<>();
    private final Map<Integer, String> groups = new HashMap<>();
    private final Map<Integer, Expense> expenses = new HashMap<>();
    private final Map<Integer, Map<Integer, Double>> splits = new HashMap<>();
    private int selectedGroup = 1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + Session.getCurrentUser());
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());

        // Group selection listener
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedGroup = groupNameToId(newVal);
                refreshUI();
            }
        });

        // Group search
        groupSearch.textProperty().addListener((obs, old, query) -> {
            if (net == null) return;
            if (query == null || query.isBlank()) {
                net.send("REQUEST_SNAPSHOT");
                return;
            }
            net.send("SEARCH_GROUP|" + query.trim());
        });
    }

    /** Called from LoginController after login succeeds */
    public void initWithNetClient(NetClient net) {
        this.net = net;

        try {
            this.net.send("REQUEST_SNAPSHOT");
        } catch (Exception e) {
            showError("Failed to request data: " + e.getMessage());
        }

        this.net.setMessageHandler(this::onMessage);
    }

    // --- Message Handling ---
    private void onMessage(String line) {
        if (line == null || line.isBlank()) return;

        // ── Handle search feedback ──
        if (line.equals("SEARCH_BEGIN")) {
            Platform.runLater(() -> groupList.getItems().clear());
            return;
        }
        if (line.startsWith("SEARCH_RESULT|")) {
            String[] p = line.split("\\|", 4);
            if (p.length >= 4) {
                String display = p[2];
                Platform.runLater(() -> {
                    if (!groupList.getItems().contains(display))
                        groupList.getItems().add(display);
                });
            }
            return;
        }
        if (line.equals("SEARCH_END")) {
            Platform.runLater(() -> {
                if (!groupList.getItems().isEmpty())
                    groupList.getSelectionModel().select(0);
            });
            return;
        }

        // ── Handle join and snapshot ──
        if (line.startsWith("JOIN_OK|")) {
            String[] p = line.split("\\|", 3);
            String gName = (p.length >= 3) ? p[2] : "group";
            Platform.runLater(() -> {
                showInfo("You joined \"" + gName + "\"");
                if (net != null) net.send("REQUEST_SNAPSHOT");
            });
            return;
        }

        if (line.equals("SNAPSHOT_BEGIN")) {
            groups.clear(); members.clear(); expenses.clear(); splits.clear();
            return;
        }
        if (line.equals("SNAPSHOT_END")) {
            Platform.runLater(this::refreshUI);
            return;
        }

        // ── Regular data from server ──
        String[] p = line.split("\\|");
        try {
            switch (p[0]) {
                case "GROUP" -> {
                    int gid = Integer.parseInt(p[1]);
                    String name = p[2];
                    groups.put(gid, name);
                }
                case "MEMBER" -> {
                    int mid = Integer.parseInt(p[1]);
                    String name = p[2];
                    members.put(mid, name);
                }
                case "EXPENSE" -> {
                    int id = Integer.parseInt(p[1]);
                    int gid = Integer.parseInt(p[2]);
                    String payer = p[3];
                    double amt = Double.parseDouble(p[4]);
                    String desc = p[5];
                    expenses.put(id, new Expense(id, gid, payer, amt, desc));
                }
                case "SPLIT" -> {
                    int eid = Integer.parseInt(p[1]);
                    int mid = Integer.parseInt(p[2]);
                    double a = Double.parseDouble(p[3]);
                    splits.computeIfAbsent(eid, k -> new HashMap<>()).put(mid, a);
                }
                case "RESET" -> {
                    int gid = Integer.parseInt(p[1]);
                    if (gid == selectedGroup) { expenses.clear(); splits.clear(); }
                }
                case "MEMBER_ADDED" -> {
                    // Trigger snapshot refresh when new member joins
                    if (net != null) net.send("REQUEST_SNAPSHOT");
                }
            }
            Platform.runLater(this::refreshUI);
        } catch (Exception e) {
            System.err.println("[Dashboard] Failed to parse message: " + line + " → " + e);
        }
    }

    // --- UI Actions ---

    @FXML
    private void onAddExpense() {
        try {
            String amountText = expenseAmount.getText();
            String desc = expenseDescription.getText();

            if (amountText == null || amountText.isBlank()) {
                showError("Please enter an amount.");
                return;
            }
            if (desc == null || desc.isBlank()) {
                desc = "General expense";
            }

            double amt = Double.parseDouble(amountText);
            if (amt <= 0) {
                showError("Please enter a valid positive amount.");
                return;
            }

            net.send("ADD_EXPENSE|" + selectedGroup + "|" + Session.getCurrentUser()
                    + "|" + amt + "|" + desc);

            expenseAmount.clear();
            expenseDescription.clear();
        } catch (NumberFormatException e) {
            showError("Invalid amount entered.");
        } catch (Exception e) {
            showError("Failed to add expense: " + e.getMessage());
        }
    }

    @FXML
    private void onSettleBalances() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Settle all balances for this group?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Settle Balances");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                net.send("SETTLE|" + selectedGroup);
                showInfo("All balances settled.");
            }
        });
    }

    @FXML
    private void onCreateGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Create New Group");
        dialog.setContentText("Enter group name:");

        dialog.showAndWait().ifPresent(gName -> {
            if (gName.isBlank()) {
                showError("Group name cannot be empty.");
                return;
            }

            try {
                net.send("ADD_GROUP|" + gName + "|General");
                showInfo("Group \"" + gName + "\" created successfully!");
            } catch (Exception e) {
                showError("Failed to create group: " + e.getMessage());
            }
        });
    }


    @FXML
    private void onRefresh() {
        if (net != null) {
            net.send("REQUEST_SNAPSHOT");
            showInfo("Dashboard refreshed.");
        }
    }

    @FXML
    private void onJoinGroup() {
        try {
            String selected = groupList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a group to join.");
                return;
            }

            int gid = groupNameToId(selected);
            net.send("JOIN_GROUP|" + gid);
            showInfo("Joining group \"" + selected + "\"...");
        } catch (Exception e) {
            showError("Failed to join group: " + e.getMessage());
        }
    }


    @FXML
    private void onViewHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            Stage stage = new Stage();
            stage.setTitle("History");
            stage.setScene(new Scene(loader.load(), 520, 420));

            var historyCtrl = loader.getController();
            if (historyCtrl instanceof HistoryController hc) {
                var rows = new ArrayList<HistoryController.Row>();
                for (Expense e : expenses.values()) {
                    if (e.groupId != selectedGroup) continue;
                    rows.add(new HistoryController.Row(e.description, e.payer, String.format("$%.2f", e.amount)));
                }
                hc.init(rows);
            }

            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to open history: " + e.getMessage());
        }
    }

    @FXML
    private void onLogout() {
        try {
            if (net != null) net.close();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Login");
            stage.setScene(new Scene(loader.load(), 320, 180));
            stage.show();
            ((Stage) welcomeLabel.getScene().getWindow()).close();
        } catch (Exception e) {
            showError("Logout failed: " + e.getMessage());
        }
    }

    // --- UI Helpers ---
    private void refreshUI() {
        ObservableList<String> gitems = FXCollections.observableArrayList(groups.values());
        groupList.setItems(gitems);
        if (!gitems.isEmpty() && groupList.getSelectionModel().getSelectedIndex() < 0) {
            groupList.getSelectionModel().select(0);
            selectedGroup = groupNameToId(gitems.get(0));
        }

        double paid = 0, owed = 0, recv = 0;
        Map<String, Double> balanceByName = new LinkedHashMap<>();

        for (Expense e : expenses.values()) {
            if (e.groupId != selectedGroup) continue;

            if (Session.getCurrentUser().equals(e.payer)) paid += e.amount;

            Map<Integer, Double> sp = splits.getOrDefault(e.id, Map.of());
            double yourShare = sp.entrySet().stream()
                    .filter(en -> Session.getCurrentUser().equals(members.get(en.getKey())))
                    .mapToDouble(Map.Entry::getValue).sum();

            if (yourShare > 0 && !Session.getCurrentUser().equals(e.payer))
                owed += yourShare;

            if (Session.getCurrentUser().equals(e.payer)) {
                double others = sp.values().stream().mapToDouble(Double::doubleValue).sum() - yourShare;
                recv += Math.max(0, others);
            }

            for (var en : sp.entrySet()) {
                String name = members.get(en.getKey());
                balanceByName.putIfAbsent(name, 0.0);
                double delta = (e.payer.equals(name) ? e.amount : 0.0) - en.getValue();
                balanceByName.put(name, balanceByName.get(name) + delta);
            }
        }

        totalPaid.setText(String.format("$%.2f", paid));
        totalOwed.setText(String.format("$%.2f", owed));
        totalReceivable.setText(String.format("$%.2f", recv));

        ObservableList<BalanceRow> rows = FXCollections.observableArrayList();
        for (var en : balanceByName.entrySet()) {
            String status = en.getValue() < 0 ? "You Owe" : "Receivable";
            rows.add(new BalanceRow(en.getKey(), String.format("$%.2f", Math.abs(en.getValue())), status));
        }
        balancesTable.setItems(rows);

        ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
        int i = 0;
        for (var en : balanceByName.entrySet()) {
            if (i++ >= 4) break;
            pie.add(new PieChart.Data(en.getKey(), Math.abs(en.getValue())));
        }
        pieChart.setData(pie);
    }

    private int groupNameToId(String name) {
        for (var en : groups.entrySet())
            if (en.getValue().equals(name)) return en.getKey();
        return 1;
    }

    private void showError(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait());
    }

    // --- Inner Class ---
    public static class Expense {
        public final int id, groupId;
        public final String payer;
        public final double amount;
        public final String description;

        public Expense(int id, int groupId, String payer, double amount, String description) {
            this.id = id;
            this.groupId = groupId;
            this.payer = payer;
            this.amount = amount;
            this.description = description;
        }
    }
}
