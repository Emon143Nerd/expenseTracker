package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.Map;
import java.util.function.Consumer;

public class GroupManagerController {

    @FXML private TextField groupName;
    @FXML private TextField groupCategory;
    @FXML private TextField memberName;
    @FXML private ComboBox<String> groupSelector;

    private Consumer<String> submitter;
    private Map<Integer, String> groups;

    public void init(Consumer<String> submitter, Map<Integer, String> groups) {
        this.submitter = submitter;
        this.groups = groups;
        if (groups != null && !groups.isEmpty()) {
            groupSelector.getItems().addAll(groups.values());
        }
    }

    @FXML
    private void onCreate() {
        String name = groupName.getText().trim();
        String cat = groupCategory.getText().trim();

        if (name.isEmpty()) {
            showError("Please enter a group name.");
            return;
        }

        submitter.accept("ADD_GROUP|" + name + "|" + (cat.isEmpty() ? "General" : cat));
        showInfo("Group '" + name + "' created successfully.");

        groupName.clear();
        groupCategory.clear();
    }

    @FXML
    private void onAddMember() {
        String member = memberName.getText().trim();
        String selectedGroup = groupSelector.getValue();

        if (member.isEmpty()) {
            showError("Please enter member name.");
            return;
        }

        if (selectedGroup == null) {
            showError("Please select a group.");
            return;
        }

        int groupId = groups.entrySet().stream()
                .filter(e -> e.getValue().equals(selectedGroup))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(-1);

        if (groupId == -1) {
            showError("Selected group not found.");
            return;
        }

        submitter.accept("ADD_MEMBER|" + member + "|" + groupId);
        showInfo("Member '" + member + "' added to group '" + selectedGroup + "'.");

        memberName.clear();
    }

    @FXML
    private void onClose() {
        ((Stage) ((Node) groupName).getScene().getWindow()).close();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
