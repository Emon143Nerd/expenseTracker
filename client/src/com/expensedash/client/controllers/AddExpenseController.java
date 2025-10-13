package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.*;
import java.util.function.Consumer;

public class AddExpenseController {

    @FXML private TextField descField;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> payerBox;
    @FXML private ListView<CheckBox> participantsList;

    private List<String> members = new ArrayList<>();
    private Consumer<String> onSubmit; // callback to send expense data
    private int groupId = 1;
    private Map<String, Integer> memberIds = new HashMap<>();

    /**
     * Initialize the Add Expense dialog
     * @param groupId current group ID
     * @param memberNames members of the group
     * @param submitter callback to send ADD_EXPENSE command
     */
    public void init(int groupId, Collection<String> memberNames, Consumer<String> submitter) {
        this.groupId = groupId;
        this.onSubmit = submitter;
        this.members = new ArrayList<>(memberNames);

        // Add current user if not in member list
        if (!members.contains(Session.getCurrentUser())) {
            members.add(Session.getCurrentUser());
        }

        // Assign stable fake IDs (temporary fix if real DB IDs aren’t exposed yet)
        memberIds.clear();
        int id = 1;
        for (String m : members) {
            memberIds.put(m, id++);
        }

        payerBox.setItems(FXCollections.observableArrayList(members));
        payerBox.getSelectionModel().select(Session.getCurrentUser());

        // Build checkbox list
        participantsList.getItems().clear();
        for (String m : members) {
            CheckBox cb = new CheckBox(m);
            cb.setSelected(true);
            participantsList.getItems().add(cb);
        }
    }

    @FXML
    private void onEqualSplit() {
        new Alert(Alert.AlertType.INFORMATION,
                "Equal split will automatically be applied when you click 'Add Expense'.",
                ButtonType.OK).showAndWait();
    }

    @FXML
    private void onAdd() {
        try {
            String amountText = amountField.getText().trim();
            if (amountText.isEmpty()) {
                showError("Please enter an amount.");
                return;
            }

            double amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                showError("Amount must be greater than zero.");
                return;
            }

            String desc = descField.getText() == null ? "" : descField.getText().trim();
            String payer = payerBox.getSelectionModel().getSelectedItem();
            if (payer == null || payer.isBlank()) {
                showError("Please select a payer.");
                return;
            }

            List<String> chosen = new ArrayList<>();
            for (CheckBox cb : participantsList.getItems()) {
                if (cb.isSelected()) chosen.add(cb.getText());
            }

            if (chosen.isEmpty()) {
                showError("Choose at least one participant.");
                return;
            }

            // Equal split calculation
            double per = Math.round((amount / chosen.size()) * 100.0) / 100.0;
            List<String> parts = new ArrayList<>();
            for (String name : chosen) {
                int memberId = memberIds.getOrDefault(name, -1);
                if (memberId == -1) continue;
                parts.add(memberId + ":" + per);
            }

            // Send to server
            String line = String.format("ADD_EXPENSE|%d|%s|%.2f|%s|%s",
                    groupId, payer, amount, desc, String.join(",", parts));

            if (onSubmit != null) onSubmit.accept(line);

            showInfo("Expense added successfully!");
            close();

        } catch (NumberFormatException e) {
            showError("Please enter a valid number for the amount.");
        } catch (Exception e) {
            showError("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        ((Stage) ((Node) amountField).getScene().getWindow()).close();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
