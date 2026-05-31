package pokergame.client.view.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import pokergame.GameContext;
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

    @FXML
    public void initialize() {
        int bankroll = getBankroll();
        bankrollLabel.setText("Bankroll: $" + formatMoney(bankroll));

        updateJoinButton(danielTableButton, DANIEL_TABLE_BUY_IN, bankroll);
        updateJoinButton(philTableButton, PHIL_TABLE_BUY_IN, bankroll);
        updateJoinButton(localTableButton, LOCAL_TABLE_BUY_IN, bankroll);
    }

    @FXML
    public void handlePlayNow() {
        int bankroll = getBankroll();
        if (bankroll >= DANIEL_TABLE_BUY_IN) {
            joinGameTable(DANIEL_TABLE_BUY_IN);
        } else if (bankroll >= LOCAL_TABLE_BUY_IN) {
            joinGameTable(LOCAL_TABLE_BUY_IN);
        } else {
            showNotEnoughMoney(LOCAL_TABLE_BUY_IN);
        }
    }

    @FXML
    public void handleProfile() {
        SceneManager.switchScene("PlayerProfile.fxml");
    }

    @FXML
    public void handleJoinDanielTable() {
        joinGameTable(DANIEL_TABLE_BUY_IN);
    }

    @FXML
    public void handleJoinPhilTable() {
        joinGameTable(PHIL_TABLE_BUY_IN);
    }

    @FXML
    public void handleJoinLocalTable() {
        joinGameTable(LOCAL_TABLE_BUY_IN);
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

    private void updateJoinButton(Button button, int buyIn, int bankroll) {
        boolean canAfford = bankroll >= buyIn;
        button.setDisable(!canAfford);
        button.setText(canAfford ? "Join Table" : "Need $" + formatMoney(buyIn));
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
}
