package pokergame.client.view.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import pokergame.GameContext;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.view.SceneManager;

public class LobbyController {

    @FXML
    public void handleProfile(ActionEvent event) {
        System.out.println("Opening Profile...");
    }

    @FXML
    public void handlePlayNow(ActionEvent event) {
        System.out.println("[Lobby] 'Play Now' clicked. Connecting to server...");

        try {
            String username = GameContext.getPlayerProfile().username();
            String serverUri = "ws://localhost:8081?user=" + username;

            boolean isConnected = PokerWebSocketClient.getInstance().connect(serverUri);
            if (isConnected) {
                System.out.println("[Lobby] ✅ Connected to game server! Loading table UI...");
                SceneManager.switchScene("GameTable.fxml");
            } else {
                System.err.println("[Lobby] ❌ Failed to connect to the game server.");
                // OPTIONAL: Show a JavaFX Alert/Error dialog to the user here
            }

        } catch (Exception e) {
            System.err.println("[Lobby] ❌ Error during table join process: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @FXML
    public void handleJoinTable(ActionEvent event) {
        SceneManager.switchScene("GameTable.fxml");
    }
}
