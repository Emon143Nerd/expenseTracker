package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.function.Consumer;

public class JoinGroupController {
    @FXML private TextField searchField;
    @FXML private ListView<String> resultList;

    private Consumer<String> sender;

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

    @FXML
    private void onJoin() {
        String selected = resultList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a group to join.").showAndWait();
            return;
        }
        String groupName = selected.split(" - ")[0];
        sender.accept("JOIN_GROUP|" + groupName + "|" + Session.getCurrentUser());
        new Alert(Alert.AlertType.INFORMATION, "Join request sent for " + groupName).showAndWait();
    }

    @FXML
    private void onClose() {
        ((Stage) resultList.getScene().getWindow()).close();
    }

    public void updateResults(String groupLine) {
        resultList.getItems().add(groupLine);
    }
}
