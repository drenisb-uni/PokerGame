package pokergame.client.view.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import pokergame.GameContext;
import pokergame.client.network.OutboundActionPayload;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.PlayerProfileDTO;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LobbyController {
    private static final int DANIEL_TABLE_BUY_IN = 500;
    private static final int PHIL_TABLE_BUY_IN = 10000;
    private static final int LOCAL_TABLE_BUY_IN = 50;
    String token = GameContext.getJwtToken();
    @FXML private Label bankrollLabel;
    @FXML private Label joinStatusLabel;
    @FXML private Button danielTableButton;
    @FXML private Button philTableButton;
    @FXML private Button localTableButton;
    
    @FXML private StackPane playNowPopupOverlay;
    @FXML private TextField botCountInput;
    @FXML private StackPane hostTablePopupOverlay;

    @FXML private StackPane joinTablePopupOverlay;
    @FXML private TextField tableIdInput;

    @FXML
    public void initialize() {
        int bankroll = getBankroll();
        bankrollLabel.setText("Bankroll: $" + formatMoney(bankroll));
    }


    @FXML
    public void handleProfile() {
        SceneManager.switchScene("PlayerProfile.fxml");
    }

    @FXML
    public void handlePlayNow() {
        showPopupAnimated(playNowPopupOverlay);
    }

    public void handleHostTable(ActionEvent actionEvent) {
        showPopupAnimated(hostTablePopupOverlay);
    }

    public void handleJoinTable(ActionEvent actionEvent) {
        showPopupAnimated(joinTablePopupOverlay);
    }

    public void handleConfirmPN(ActionEvent actionEvent) {
    }

    public void hidePopupPN(ActionEvent actionEvent) {
        hidePopupAnimated(playNowPopupOverlay);
    }

    public void handleConfirmHT(ActionEvent actionEvent) {
    }

    public void hidePopupHT(ActionEvent actionEvent) {
        hidePopupAnimated(hostTablePopupOverlay);
    }

    public void handleConfirmJT(ActionEvent actionEvent) {
        playButtonPress((Button) actionEvent.getSource());
        try {
            int tableId = Integer.parseInt(tableIdInput.getText().trim());

            if (tableId != 6) {
                System.out.println("Table ID is invalid");
                return;
            }

            sendAction("JOIN_TABLE", tableId);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    public void hidePopupJT(ActionEvent actionEvent) {
        hidePopupAnimated(joinTablePopupOverlay);
    }


    private void sendAction(String actionType, int amount) {
        PokerWebSocketClient client = PokerWebSocketClient.getInstance();
        if (client != null && client.isOpen()) {
            client.sendPayload(new OutboundActionPayload(actionType, amount));
        }
    }

    private void joinGameTable(int buyIn) {
        int bankroll = getBankroll();
        if (bankroll < buyIn) {
            showNotEnoughMoney(buyIn);
            return;
        }

        System.out.println("[Lobby] Connecting to game server...");

        if (token == null || token.isBlank()) {
            System.err.println("FATAL: Trying to connect with a null token!");
            return;
        }

        try {
            String encodedToken = URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
            String serverUri = "ws://localhost:8081/?token=" + encodedToken + "&buyIn=1000";

            boolean isConnected = PokerWebSocketClient.connect(serverUri);
            if (isConnected) {
                System.out.println("[Lobby] Connected to game server. Loading table UI...");
                SceneManager.switchScene("GameTable.fxml");
            } else {
                joinStatusLabel.setText("Could not connect to the game server.");
                System.err.println("[Lobby] Failed to connect to the game server.");
            }
        } catch (Exception e) {
            joinStatusLabel.setText("Could not join the table.");
            System.err.println("[Lobby] Error during table join process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getBankroll() {
        PlayerProfileDTO profile = GameContext.getPlayerProfile();
        return profile == null ? 0 : profile.totalBankroll();
    }

    private void showNotEnoughMoney(int buyIn) {
        joinStatusLabel.setText("You need $" + formatMoney(buyIn) + " to join that table.");
    }

    private String formatMoney(int amount) {
        return String.format("%,d", amount);
    }

    private void showPopupAnimated(StackPane popupOverlay) {
        if (popupOverlay == null) {
            return;
        }

        popupOverlay.setOpacity(0);
        popupOverlay.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(320), popupOverlay);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void hidePopupAnimated(StackPane popupOverlay) {
        if (popupOverlay == null || !popupOverlay.isVisible()) {
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), popupOverlay);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            popupOverlay.setVisible(false);
            popupOverlay.setOpacity(1);
        });
        fadeOut.play();
    }


    private void playButtonPress(Button button) {
        if (button == null) {
            return;
        }

        ScaleTransition press = new ScaleTransition(Duration.millis(110), button);
        press.setToX(0.94);
        press.setToY(0.94);

        ScaleTransition release = new ScaleTransition(Duration.millis(170), button);
        release.setToX(1);
        release.setToY(1);

        new SequentialTransition(press, release).play();
    }
}
