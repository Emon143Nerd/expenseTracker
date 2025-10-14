package com.expensedash.client.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class JoinRequestsController {

    @FXML private ListView<String> requestList;
    private Consumer<String> sender;
    private String creatorName;

    public void init(Consumer<String> sender, String creatorName) {
        this.sender = sender;
        this.creatorName = creatorName;

        // ask server for pending join requests
        sender.accept("GET_JOIN_REQUESTS|" + creatorName);
    }

    public void addRequest(String display) {
        Platform.runLater(() -> requestList.getItems().add(display));
    }

    @FXML
    private void onApprove() {
        String selected = requestList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a request to approve.").showAndWait();
            return;
        }
        String[] parts = selected.split(" - ");
        sender.accept("APPROVE_REQUEST|" + parts[0] + "|" + creatorName);
        new Alert(Alert.AlertType.INFORMATION, "Request approved.").showAndWait();
        requestList.getItems().remove(selected);
    }

    @FXML
    private void onReject() {
        String selected = requestList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Select a request to reject.").showAndWait();
            return;
        }
        String[] parts = selected.split(" - ");
        sender.accept("REJECT_REQUEST|" + parts[0] + "|" + creatorName);
        new Alert(Alert.AlertType.INFORMATION, "Request rejected.").showAndWait();
        requestList.getItems().remove(selected);
    }

    @FXML
    private void onClose() {
        ((Stage) requestList.getScene().getWindow()).close();
    }
}
