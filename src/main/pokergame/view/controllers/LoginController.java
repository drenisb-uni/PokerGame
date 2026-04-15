package pokergame.view.controllers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import javafx.scene.input.MouseEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import pokergame.GameContext;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.repository.PlayerRepository;
import pokergame.view.SceneManager;

import java.time.LocalDateTime;
import java.util.UUID;

public class LoginController {

    @FXML private TextField loginUsername;
    @FXML private PasswordField loginPassword;
    @FXML private Label loginErrorLabel;

    @FXML private TextField regUsername;
    @FXML private TextField regEmail;
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regRepeatPassword;
    @FXML private Label regErrorLabel;

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = loginUsername.getText().trim();
        String password = loginPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError(loginErrorLabel, "Please fill in all fields.");
            return;
        }

        System.out.println("Attempting to log in user: " + username);

        try {
            PlayerRepository repo = GameContext.getPlayerRepository();
            PlayerProfileDTO userProfile = repo.findProfileByUsername(username);

            if (userProfile == null) {
                showError(loginErrorLabel, "Invalid username or password.");
                return;
            }

            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), userProfile.passwordHash());

            if (result.verified) {
                System.out.println("Login successful for: " + username);

                // TODO: Store 'userProfile' in your GameContext so the Lobby knows who is playing!

                SceneManager.switchScene("Lobby.fxml");
            } else {
                showError(loginErrorLabel, "Invalid username or password.");
            }

        } catch (Exception e) {
            showError(loginErrorLabel, "A database error occurred. Please try again.");
            e.printStackTrace();
        }
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = regUsername.getText().trim();
        String email = regEmail.getText().trim();
        String password = regPassword.getText();
        String repeatPassword = regRepeatPassword.getText();


        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError(regErrorLabel, "All fields are required!");
            return;
        }

        if (!email.contains("@")) {
            showError(regErrorLabel, "Email address is invalid.!");
            return;
        }

        if (password.length() < 6) {
            showError(regErrorLabel, "Password must be at least 6 characters.");
            return;
        }

        if (repeatPassword.equals(password)) {
            showError(regErrorLabel, "Passwords don't match.");
            return;
        }

        String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        String newId = UUID.randomUUID().toString();

        PlayerProfileDTO newUser = new PlayerProfileDTO(
                newId, username, email, hashedPassword, 1000, LocalDateTime.now()
        );

        try {
            PlayerRepository repo = GameContext.getPlayerRepository();
            repo.saveProfile(newUser);
            System.out.println("User registered successfully!");
            SceneManager.switchScene("Lobby.fxml");
        }
        catch (Exception e) {
            showError(regErrorLabel, "Error saving user. Username or Email might already exist.");
            e.printStackTrace();
        }
    }
    @FXML
    public void switchToSignup(MouseEvent event) {
        pokergame.view.SceneManager.switchScene("Signup.fxml");
    }
    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }


}