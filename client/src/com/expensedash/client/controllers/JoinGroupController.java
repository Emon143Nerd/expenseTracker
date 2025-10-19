package com.expensedash.client.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.function.Consumer;

/**
 * JoinGroupController — simplified instant join version.
 */
public class JoinGroupController {

    @FXML private TextField searchField;
    @FXML private ListView<String> resultsList;
    @FXML private Button joinButton;
    @FXML private Label statusLabel;

    private Consumer<String> send;
    private final ObservableList<String> groupResults = FXCollections.observableArrayList();
    private final java.util.Map<String, Integer> groupNameToId = new java.util.HashMap<>();

    public void init(Consumer<String> sender) {
        this.send = sender;
        resultsList.setItems(groupResults);

        // Search as user types
        searchField.textProperty().addListener((obs, old, query) -> {
            if (query == null || query.isBlank()) {
                groupResults.clear();
                groupNameToId.clear();
                return;
            }
            send.accept("SEARCH_GROUP|" + query.trim());
        });
    }

    /**
     * Called externally when search results arrive (optional future hook).
     */
    public void addGroupResult(int gid, String name, String category) {
        Platform.runLater(() -> {
            if (!groupNameToId.containsKey(name)) {
                groupResults.add(name);
                groupNameToId.put(name, gid);
            }
        });
    }

    @FXML
    private void onJoinSelectedGroup() {
        String selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a group to join.");
            return;
        }

        Integer gid = groupNameToId.get(selected);
        if (gid == null) {
            statusLabel.setText("Group ID not found.");
            return;
        }

        try {
            send.accept("JOIN_GROUP|" + gid);
            statusLabel.setText("Joining group \"" + selected + "\"...");
        } catch (Exception e) {
            statusLabel.setText("Error joining: " + e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        searchField.getScene().getWindow().hide();
    }
}
