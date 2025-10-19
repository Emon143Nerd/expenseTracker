package com.expensedash.client.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;

public class HistoryController {

    @FXML private TableView<Row> historyTable;
    @FXML private TableColumn<Row, String> colDescription;
    @FXML private TableColumn<Row, String> colPayer;
    @FXML private TableColumn<Row, String> colAmount;

    @FXML
    private void onClose() {
        Stage stage = (Stage) historyTable.getScene().getWindow();
        stage.close();
    }

    public void init(List<Row> rows) {
        ObservableList<Row> data = FXCollections.observableArrayList(rows);
        colDescription.setCellValueFactory(c -> c.getValue().descriptionProperty());
        colPayer.setCellValueFactory(c -> c.getValue().payerProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        historyTable.setItems(data);
    }

    public static class Row {
        private final javafx.beans.property.SimpleStringProperty description;
        private final javafx.beans.property.SimpleStringProperty payer;
        private final javafx.beans.property.SimpleStringProperty amount;

        public Row(String description, String payer, String amount) {
            this.description = new javafx.beans.property.SimpleStringProperty(description);
            this.payer = new javafx.beans.property.SimpleStringProperty(payer);
            this.amount = new javafx.beans.property.SimpleStringProperty(amount);
        }

        public javafx.beans.property.StringProperty descriptionProperty() { return description; }
        public javafx.beans.property.StringProperty payerProperty() { return payer; }
        public javafx.beans.property.StringProperty amountProperty() { return amount; }
    }
}
