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

/**
 * DashboardController — main controller for the ExpenseDash dashboard.
 * Handles data display, user actions, and server communication.
 */
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

    // --- Network and Data Structures ---
    private NetClient net;
    private final Map<Integer, String> members = new HashMap<>();
    private final Map<Integer, String> groups = new HashMap<>();
    private final Map<Integer, Expense> expenses = new HashMap<>();
    private final Map<Integer, Map<Integer, Double>> splits = new HashMap<>();
    private int selectedGroup = 1;

    // --- Initialization ---
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        welcomeLabel.setText("Welcome, " + Session.getCurrentUser());
        colName.setCellValueFactory(c -> c.getValue().nameProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());

        // Listen for group selection changes
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedGroup = groupNameToId(newVal);
                refreshUI();
            }
        });

        groupSearch.textProperty().addListener((obs, old, query) -> {
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

        // Listen for messages from the server
        this.net.setMessageHandler(this::onMessage);
    }

    // --- Message Handling from Server ---
    private void onMessage(String line) {
        if (line == null || line.isBlank()) return;

        switch (line) {
            case "SNAPSHOT_BEGIN" -> {
                groups.clear();
                members.clear();
                expenses.clear();
                splits.clear();
                return;
            }
            case "SNAPSHOT_END" -> {
                Platform.runLater(this::refreshUI);
                return;
            }
        }

        String[] p = line.split("\\|");
        try {
            switch (p[0]) {
                case "GROUP" -> {
                    int gid = Integer.parseInt(p[1]);
                    String name = p[2];
                    groups.put(gid, name);
                    Platform.runLater(() -> {
                        if (!groupList.getItems().contains(name))
                            groupList.getItems().add(name);
                    });
                }

                case "MEMBER" -> members.put(Integer.parseInt(p[1]), p[2]);
                case "EXPENSE" -> expenses.put(
                        Integer.parseInt(p[1]),
                        new Expense(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                                p[3], Double.parseDouble(p[4]), p[5]));
                case "SPLIT" -> splits
                        .computeIfAbsent(Integer.parseInt(p[1]), k -> new HashMap<>())
                        .put(Integer.parseInt(p[2]), Double.parseDouble(p[3]));
                case "RESET" -> {
                    int gid = Integer.parseInt(p[1]);
                    if (gid == selectedGroup) {
                        expenses.clear();
                        splits.clear();
                        Platform.runLater(this::refreshUI);
                    }
                }
                case "SEARCH_BEGIN" -> {
                    Platform.runLater(() -> groupList.getItems().clear());
                    return;
                }
                case "SEARCH_END" -> {
                    // Optionally, auto-select first result
                    Platform.runLater(() -> {
                        if (!groupList.getItems().isEmpty())
                            groupList.getSelectionModel().select(0);
                    });
                    return;
                }

            }
            Platform.runLater(this::refreshUI);
        } catch (Exception e) {
            System.err.println("[Dashboard] Failed to parse message: " + line + " → " + e);
        }
    }

    // --- Button Handlers ---

    /** Opens the Group Manager dialog */
    @FXML
    private void onManageGroups() {
        try {
            var url = getClass().getResource("/fxml/group_manager.fxml");
            System.out.println("[DEBUG] group_manager.fxml URL = " + url);

            FXMLLoader loader = new FXMLLoader(url);
            Stage stage = new Stage();
            stage.setTitle("Groups & Members");
            stage.setScene(new Scene(loader.load(), 420, 420));

            GroupManagerController controller = loader.getController();
            if (controller == null)
                throw new IllegalStateException("GroupManagerController not loaded from FXML");

            // ✅ Defensive checks
            if (net == null)
                throw new IllegalStateException("Network client (net) is null — initWithNetClient() not called.");
            if (groups == null)
                throw new IllegalStateException("Groups map is null — not initialized.");

            System.out.println("[DEBUG] net = " + net);
            System.out.println("[DEBUG] groups = " + groups);

            controller.init(net::send, groups);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "Failed to open Group Manager:\n" + e.getMessage(),
                    ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void onJoinGroup() {
        String selected = groupList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isEmpty()) {
            showError("Please select a group from the list to join.");
            return;
        }

        int gid = groupNameToId(selected);
        if (gid == -1) {
            showError("Could not resolve group ID — try searching again.");
            return;
        }

        try {
            net.send("JOIN_GROUP|" + gid + "|" + Session.getCurrentUser());
            showInfo("Join request sent for group '" + selected + "'.");
            // Optionally refresh data after join
            net.send("REQUEST_SNAPSHOT");
        } catch (Exception e) {
            showError("Failed to send join request: " + e.getMessage());
        }
    }




    /** Adds a new expense */
    @FXML
    private void onAddExpense() {
        TextInputDialog dialog = new TextInputDialog("12.50");
        dialog.setHeaderText("Add expense (payer: " + Session.getCurrentUser() + ")");
        dialog.setContentText("Enter amount:");

        dialog.showAndWait().ifPresent(val -> {
            try {
                double amt = Double.parseDouble(val);
                if (amt <= 0) {
                    showError("Please enter a valid positive amount.");
                    return;
                }

                List<Integer> mids = new ArrayList<>(members.keySet());
                if (mids.isEmpty()) {
                    showError("No members found in this group.");
                    return;
                }

                double per = Math.round((amt / mids.size()) * 100.0) / 100.0;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mids.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(mids.get(i)).append(":").append(per);
                }

                net.send("ADD_EXPENSE|" + selectedGroup + "|" + Session.getCurrentUser()
                        + "|" + amt + "|New expense|" + sb);

                showInfo("Expense added successfully!");
            } catch (NumberFormatException e) {
                showError("Invalid amount entered.");
            } catch (Exception e) {
                showError("Failed to add expense: " + e.getMessage());
            }
        });
    }

    /** Settles all balances */
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

    /** Shows the expense history */
    @FXML
    private void onViewHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            Stage stage = new Stage();
            stage.setTitle("History");
            stage.setScene(new Scene(loader.load(), 520, 420));

            var historyCtrl = loader.getController();
            if (historyCtrl instanceof com.expensedash.client.controllers.HistoryController hc) {
                var rows = new ArrayList<com.expensedash.client.controllers.HistoryController.Row>();
                for (Expense e : expenses.values()) {
                    if (e.groupId != selectedGroup) continue;
                    rows.add(new com.expensedash.client.controllers.HistoryController.Row(
                            e.description, e.payer, String.format("$%.2f", e.amount)));
                }
                hc.init(rows);
            }

            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to open history: " + e.getMessage());
        }
    }

    /** Logs the user out */
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

    /** Refreshes the dashboard display */
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

    // --- Inner Data Model ---
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
