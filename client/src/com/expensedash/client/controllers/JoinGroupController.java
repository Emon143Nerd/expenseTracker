package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.*;
import java.util.function.Consumer;

public class JoinGroupController {

    @FXML private TextField searchField;
    @FXML private ListView<String> resultList;

    private Consumer<String> sender;
    private final Map<String, Integer> nameToId = new HashMap<>();

    public void init(Consumer<String> sender) {
        this.sender = sender;
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Enter a search term.").showAndWait();
            return;
        }
        sender.accept("SEARCH_GROUP|" + query);
    }

    public void updateResults(String display, int groupId) {
        nameToId.put(display, groupId);
        resultList.getItems().add(display);
    }

    public void clearResults() {
        resultList.getItems().clear();
        nameToId.clear();
    }

    @FXML
    private void onJoin() {
        String selected = resultList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a group to join.").showAndWait();
            return;
        }
        int gid = nameToId.get(selected);
        sender.accept("JOIN_GROUP|" + gid + "|" + Session.getCurrentUser());
        new Alert(Alert.AlertType.INFORMATION, "Join request sent to group creator.").showAndWait();
    }

    @FXML
    private void onClose() {
        ((Stage) resultList.getScene().getWindow()).close();
    }
}
