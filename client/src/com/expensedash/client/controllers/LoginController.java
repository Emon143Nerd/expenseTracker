package com.expensedash.client.controllers;

import com.expensedash.client.Session;
import com.expensedash.client.net.NetClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.security.MessageDigest;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;

    private final NetClient net = new NetClient();
    private String serverIP;

    /** Hash passwords using SHA-256 */
    private String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Login button clicked */
    @FXML private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        TextInputDialog d = new TextInputDialog("127.0.0.1");
        d.setHeaderText("Enter server IP address:");
        d.setContentText("Server IP:");
        d.showAndWait().ifPresent(ip -> {
            this.serverIP = ip.trim();
            connectAndLogin(username, password);
        });
    }

    /** Register button clicked */
    @FXML private void onRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        TextInputDialog d = new TextInputDialog("127.0.0.1");
        d.setHeaderText("Enter server IP address:");
        d.setContentText("Server IP:");
        d.showAndWait().ifPresent(ip -> {
            this.serverIP = ip.trim();
            connectAndRegister(username, password);
        });
    }

    /** Connect and send LOGIN */
    private void connectAndLogin(String username, String password) {
        try {
            net.connect(serverIP, 5055, line -> {
                if ("LOGIN_OK".equals(line)) {
                    Platform.runLater(() -> {
                        Session.setCurrentUser(username);
                        showInfo("Login successful!");
                        openDashboard();
                    });
                } else if (line.startsWith("LOGIN_FAIL")) {
                    Platform.runLater(() -> showError("Invalid username or password."));
                } else if (line.startsWith("LOGIN_ERR")) {
                    Platform.runLater(() -> showError("Server error: " + line));
                }
            });

            net.send("LOGIN|" + username + "|" + hash(password));

        } catch (Exception e) {
            showError("Could not connect to server: " + e.getMessage());
        }
    }

    /** Connect and send REGISTER */
    private void connectAndRegister(String username, String password) {
        try {
            net.connect(serverIP, 5055, line -> {
                switch (line) {
                    case "REGISTER_OK" -> Platform.runLater(() ->
                            showInfo("Registered successfully! Please log in."));
                    case "REGISTER_DUP" -> Platform.runLater(() ->
                            showError("Username already exists."));
                    default -> {
                        if (line.startsWith("REGISTER_ERR|"))
                            Platform.runLater(() ->
                                    showError("Registration failed: " + line.substring("REGISTER_ERR|".length())));
                        else
                            Platform.runLater(() ->
                                    showError("Unexpected response: " + line));
                    }
                }
            });

            net.send("REGISTER|" + username + "|" + hash(password));

        } catch (Exception e) {
            showError("Could not connect to server: " + e.getMessage());
        }
    }

    /** Open dashboard scene after successful login */
    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Stage stage = new Stage();
            stage.setTitle("Expense Dashboard");
            Scene scene = new Scene(loader.load(), 1200, 700);
            scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setMaximized(true);

            // Pass same NetClient instance
            var controller = loader.getController();
            if (controller instanceof DashboardController dc) {
                dc.initWithNetClient(net);
            }

            // Close login window
            ((Stage) loginButton.getScene().getWindow()).close();
            stage.show();

        } catch (Exception e) {
            showError("Failed to open dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }

    private void showInfo(String msg) {
        Platform.runLater(() ->
                new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait());
    }
}
