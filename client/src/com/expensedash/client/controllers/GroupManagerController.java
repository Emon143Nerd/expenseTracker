package com.expensedash.client.controllers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class GroupManagerController {
    @FXML private TextField groupName;
    @FXML private TextField groupCategory;
    @FXML private TextField memberName;
    @FXML private TextField memberGroupId;

    private Consumer<String> submitter;

    public void init(Consumer<String> submitter){ this.submitter = submitter; }

    @FXML private void onCreate(){
        String name = groupName.getText(); String cat = groupCategory.getText();
        if (name==null || name.isBlank()){ new Alert(Alert.AlertType.ERROR,"Group name required").showAndWait(); return; }
        submitter.accept("ADD_GROUP|" + name + "|" + (cat==null? "":cat));
        new Alert(Alert.AlertType.INFORMATION,"Group created").showAndWait();
    }
    @FXML private void onAddMember(){
        try{
            String n = memberName.getText(); int gid = Integer.parseInt(memberGroupId.getText());
            if (n==null || n.isBlank()){ new Alert(Alert.AlertType.ERROR,"Member name required").showAndWait(); return; }
            submitter.accept("ADD_MEMBER|" + n + "|" + gid);
            new Alert(Alert.AlertType.INFORMATION,"Member added").showAndWait();
        }catch(Exception e){
            new Alert(Alert.AlertType.ERROR,"Enter a valid group id").showAndWait();
        }
    }
    @FXML private void onClose(){ ((Stage)((Node)groupName).getScene().getWindow()).close(); }
}
