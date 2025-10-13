package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.*;

public class AddExpenseController {
    @FXML private TextField descField;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> payerBox;
    @FXML private ListView<CheckBox> participantsList;

    private List<String> members = new ArrayList<>();
    private java.util.function.Consumer<String> onSubmit; // sends protocol line
    private int groupId = 1;

    public void init(int groupId, Collection<String> memberNames, java.util.function.Consumer<String> submitter){
        this.groupId = groupId;
        this.onSubmit = submitter;
        this.members = new ArrayList<>(memberNames);
        // ensure current user exists in list
        if (!members.contains(Session.getCurrentUser())) members.add(Session.getCurrentUser());
        payerBox.setItems(FXCollections.observableArrayList(members));
        payerBox.getSelectionModel().select(Session.getCurrentUser());
        for (String m : members){
            CheckBox cb = new CheckBox(m);
            cb.setSelected(true);
            participantsList.getItems().add(cb);
        }
    }

    @FXML private void onEqualSplit(){
        // noop; equal split is computed on submit
        new Alert(Alert.AlertType.INFORMATION, "Equal split will be applied on Add.").showAndWait();
    }

    @FXML private void onAdd(){
        try{
            double amount = Double.parseDouble(amountField.getText());
            String desc = descField.getText()==null? "": descField.getText();
            String payer = payerBox.getSelectionModel().getSelectedItem();
            List<String> parts = new ArrayList<>();
            List<String> chosen = new ArrayList<>();
            for (CheckBox cb: participantsList.getItems()) if (cb.isSelected()) chosen.add(cb.getText());
            if (chosen.isEmpty()) { new Alert(Alert.AlertType.ERROR, "Choose at least one participant").showAndWait(); return; }
            double per = Math.round((amount / chosen.size())*100.0)/100.0;
            // minimal mapping: member name to synthetic id via index
            for (String name: chosen){
                int memberId = members.indexOf(name)+1;
                parts.add(memberId + ":" + per);
            }
            String line = "ADD_EXPENSE|" + groupId + "|" + payer + "|" + amount + "|" + desc + "|" + String.join(",", parts);
            if (onSubmit != null) onSubmit.accept(line);
            close();
        }catch(Exception e){
            new Alert(Alert.AlertType.ERROR, "Invalid amount").showAndWait();
        }
    }
    @FXML private void onCancel(){ close(); }
    private void close(){ ((Stage)((Node)amountField).getScene().getWindow()).close(); }
}
