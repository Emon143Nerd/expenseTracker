package com.expensedash.client.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Map;
import java.util.function.Consumer;

public class GroupManagerController {

    @FXML private TextField groupNameField;
    @FXML private TextField categoryField;
    @FXML private ListView<String> groupList;
    @FXML private Label statusLabel;
    @FXML private Button addGroupButton;

    private Consumer<String> send;
    private Map<Integer, String> existingGroups;
    private final ObservableList<String> groupItems = FXCollections.observableArrayList();

    public void init(Consumer<String> sender, Map<Integer, String> groups) {
        this.send = sender;
        this.existingGroups = groups;
        groupItems.addAll(groups.values());
        groupList.setItems(groupItems);
    }

    @FXML
    private void onAddGroup() {
        String name = groupNameField.getText();
        String category = categoryField.getText();

        if (name == null || name.isBlank()) {
            statusLabel.setText("Group name cannot be empty.");
            return;
        }

        try {
            send.accept("ADD_GROUP|" + name + "|" + (category == null ? "" : category));
            statusLabel.setText("Creating group \"" + name + "\"...");
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Called externally (optional) when a new group is confirmed by server.
     */
    public void addGroupToList(String name) {
        Platform.runLater(() -> {
            if (!groupItems.contains(name)) {
                groupItems.add(name);
                statusLabel.setText("Group \"" + name + "\" added!");
            }
        });
    }

    @FXML
    private void onClose() {
        groupNameField.getScene().getWindow().hide();
    }
}
