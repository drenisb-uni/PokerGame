package pokergame.client.view.controllers;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import pokergame.GameContext;
import pokergame.client.network.AuthNetworkClient;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.PlayerProfileDTO;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.util.concurrent.CompletableFuture;

public class LoginController {

    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginErrorLabel;

    @FXML private TextField regUsername;
    @FXML private TextField regEmail;
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regRepeatPassword;
    @FXML private Label regErrorLabel;

    // Instantiated boundary service layer
    private final AuthNetworkClient authClient = new AuthNetworkClient();

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = loginUsername.getText().trim();
        String password = loginPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(loginErrorLabel, "All fields are required!");
            return;
        }

        loginErrorLabel.setVisible(false);
        System.out.println("Dispatching login request for user: " + username);

        // Run network call on a background worker thread to keep UI completely fluid
        CompletableFuture.supplyAsync(() -> authClient.login(username, password))
                .thenAccept(userProfile -> {
                    // Snap back to the JavaFX application thread to handle UI modifications
                    Platform.runLater(() -> {
                        if (userProfile != null) {
                            System.out.println("Login verified by server for: " + username);
                            GameContext.setPlayerProfile(userProfile);
                            SceneManager.switchScene("Lobby.fxml");
                        } else {
                            showError(loginErrorLabel, "Invalid username or password.");
                        }
                    });
                });
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = regUsername.getText().trim();
        String email = regEmail.getText().trim();
        String password = regPassword.getText();
        String repeatPassword = regRepeatPassword.getText();

        // Standard local client side validation guard checks
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError(regErrorLabel, "All fields are required!");
            return;
        }
        if (!email.contains("@")) {
            showError(regErrorLabel, "Email address is invalid!");
            return;
        }
        if (password.length() < 6) {
            showError(regErrorLabel, "Password must be at least 6 characters.");
            return;
        }
        if (!repeatPassword.equals(password)) {
            showError(regErrorLabel, "Passwords don't match.");
            return;
        }

        regErrorLabel.setVisible(false);
        System.out.println("Dispatching registration payload for user: " + username);

        // Run network task asynchronously
        CompletableFuture.supplyAsync(() -> authClient.register(username, email, password))
                .thenAccept(isRegistrationSuccessful -> {
                    Platform.runLater(() -> {
                        if (isRegistrationSuccessful) {
                            System.out.println("User registered successfully via backend cluster!");
                            // Redirect player to login screen or auto-switch to lobby
                            SceneManager.switchScene("Login.fxml");
                        } else {
                            showError(regErrorLabel, "Error saving user. Username or Email might already exist.");
                        }
                    });
                });
    }
    @FXML
    public void handleForgotPassword(ActionEvent event) {
        // 1. Create a built-in JavaFX popup dialog
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Forgot Password");
        dialog.setHeaderText("Password Reset Request");
        dialog.setContentText("Enter your registered username:");

        // 2. Wait for the user to type something and hit "OK"
        dialog.showAndWait().ifPresent(username -> {
            if (username.trim().isEmpty()) {
                showError(loginErrorLabel, "Username cannot be empty for a reset!");
                return;
            }

            loginErrorLabel.setVisible(false);
            System.out.println("Dispatching password reset request for: " + username);

            // 3. Fire the network request asynchronously (just like login)
            CompletableFuture.supplyAsync(() -> authClient.requestPasswordReset(username.trim()))
                    .thenAccept(isResetSuccessful -> {
                        Platform.runLater(() -> {
                            // 4. Show a success or failure popup
                            Alert alert = new Alert(isResetSuccessful ? AlertType.INFORMATION : AlertType.ERROR);
                            alert.setTitle("Password Reset Status");
                            alert.setHeaderText(null);

                            if (isResetSuccessful) {
                                alert.setContentText("Success! The server has generated a temporary password. Check the backend console.");
                            } else {
                                alert.setContentText("Error: Could not reset password. User might not exist.");
                            }
                            alert.showAndWait();
                        });
                    });
        });
    }
    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}