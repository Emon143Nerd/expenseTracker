package com.expensedash.client.controllers;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;

public class HistoryController {
    @FXML private TableView<Row> table;
    @FXML private TableColumn<Row, String> colDesc;
    @FXML private TableColumn<Row, String> colPayer;
    @FXML private TableColumn<Row, String> colAmount;

    public record Row(String desc, String payer, String amount){}

    public void init(List<Row> rows){
        colDesc.setCellValueFactory(c-> new javafx.beans.property.SimpleStringProperty(c.getValue().desc()));
        colPayer.setCellValueFactory(c-> new javafx.beans.property.SimpleStringProperty(c.getValue().payer()));
        colAmount.setCellValueFactory(c-> new javafx.beans.property.SimpleStringProperty(c.getValue().amount()));
        table.setItems(FXCollections.observableArrayList(rows));
    }

    @FXML private void onClose(){
        Stage s = (Stage)((Node)table).getScene().getWindow();
        s.close();
    }
}
