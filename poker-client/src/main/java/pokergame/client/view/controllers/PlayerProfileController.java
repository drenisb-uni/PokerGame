package pokergame.client.view.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import pokergame.GameContext;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.PlayerProfileDTO;

import java.time.format.DateTimeFormatter;

public class PlayerProfileController {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    @FXML private Label usernameTitleLabel;
    @FXML private Label emailTitleLabel;
    @FXML private Label bankrollLabel;
    @FXML private Label createdAtLabel;
    @FXML private Label usernameValueLabel;
    @FXML private Label emailValueLabel;
    @FXML private Label playerIdLabel;
    @FXML private Label bankrollValueLabel;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        PlayerProfileDTO profile = GameContext.getPlayerProfile();
        if (profile == null) {
            showEmptyProfile();
            return;
        }

        String email = safeText(profile.email(), "No email saved");
        String bankroll = "$" + formatMoney(profile.totalBankroll());

        usernameTitleLabel.setText(safeText(profile.username(), "Player"));
        emailTitleLabel.setText(email);
        bankrollLabel.setText(bankroll);
        createdAtLabel.setText(profile.createdAt() == null ? "Unknown" : profile.createdAt().format(DATE_FORMAT));

        usernameValueLabel.setText(safeText(profile.username(), "-"));
        emailValueLabel.setText(email);
        playerIdLabel.setText(safeText(profile.id(), "-"));
        bankrollValueLabel.setText(bankroll);
        statusLabel.setText("Profile loaded from your current session.");
    }

    @FXML
    public void handleBackToLobby() {
        SceneManager.switchScene("Lobby.fxml");
    }

    @FXML
    public void handleLogout() {
        PokerWebSocketClient client = PokerWebSocketClient.getInstance();
        if (client != null && client.isOpen()) {
            client.close();
        }
        SceneManager.switchScene("Login.fxml");
    }

    private void showEmptyProfile() {
        usernameTitleLabel.setText("No player loaded");
        emailTitleLabel.setText("Log in to see your profile");
        bankrollLabel.setText("$0");
        createdAtLabel.setText("Unknown");
        usernameValueLabel.setText("-");
        emailValueLabel.setText("-");
        playerIdLabel.setText("-");
        bankrollValueLabel.setText("-");
        statusLabel.setText("No active profile was found.");
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatMoney(int amount) {
        return String.format("%,d", amount);
    }
}
