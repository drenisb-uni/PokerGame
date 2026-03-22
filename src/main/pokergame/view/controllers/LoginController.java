package pokergame.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import pokergame.view.SceneManager;

public class LoginController {

    // These variables link directly to the UI components in your FXML
    @FXML
    private TextField usernameField;

    @FXML
    private Label errorLabel;

    @FXML
    public void onLoginButtonClicked(ActionEvent event) {
        String username = usernameField.getText().trim();

        if (username.isEmpty()) {
            showError("Username cannot be empty!");
            return;
        }

        if (username.length() < 3) {
            showError("Username must be at least 3 characters!");
            return;
        }

        System.out.println("Attempting to login/signup user: " + username);

        SceneManager.switchScene("Lobby.fxml");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}