package pokergame.client.view.controllers.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import pokergame.GameContext;
import pokergame.client.network.OutboundActionPayload;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.PlayerProfileDTO;

import java.net.URLEncoder;

import org.json.JSONObject;

public class LobbyController {
    private boolean isJoiningTable = false;

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
    @FXML private Spinner<Integer> botCountInput;

    @FXML private StackPane hostTablePopupOverlay;
    @FXML private TextField buyInInput;

    @FXML private StackPane joinTablePopupOverlay;
    @FXML private TextField tableIdInput;
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());;

    @FXML
    public void initialize() {
        isJoiningTable = false;
        int bankroll = getBankroll();
        bankrollLabel.setText("Bankroll: $" + formatMoney(bankroll));

        String token = GameContext.getJwtToken();
        int defaultBuyIn = 1000;

        // 2. Build your connection URI string with required parameters
        String serverUri = "ws://localhost:8081?token=" + token + "&buyIn=" + defaultBuyIn;

        System.out.println("[Lobby] Attempting to initialize WebSocket client...");

        boolean isConnected = PokerWebSocketClient.connect(serverUri);

        if (isConnected) {
            System.out.println("[Lobby] Connection verified.");
        } else {
            System.err.println("[Lobby Failed] Could not connect to the Poker Server.");
        }
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

    @FXML
    public void handleConfirmPN(ActionEvent event) {

        if (event.getSource() instanceof Node) {
            ((Node) event.getSource()).setDisable(true);
        }

        isJoiningTable = true;

        // 1. THE GUARD: Ensure we are actually connected before doing anything!
        if (PokerWebSocketClient.getInstance() == null || !PokerWebSocketClient.getInstance().isOpen()) {
            System.err.println("[Lobby] Cannot start Play Now: WebSocket is not connected!");
            // Optional: Trigger your reconnect logic or show an error alert here
            return;
        }

        // 2. Get the requested number of bots from the Spinner
        int numBots = 0;
        if (botCountInput != null && botCountInput.getValue() != null) {
            numBots = botCountInput.getValue();
        }

        System.out.println("[Lobby] Starting 'Play Now' sequence with " + numBots + " bots...");

        try {
            JSONObject request = new JSONObject();
            request.put("action", "CREATE_TABLE"); // or "PLAY_NOW"
            request.put("buy-in", 1000);
            request.put("botCount", numBots);
            request.put("startImmediately", true);
            PokerWebSocketClient.getInstance().send(request.toString());

        } catch (Exception e) {
            System.err.println("[Lobby FATAL] Failed to send Play Now commands.");
            e.printStackTrace();
        }
    }

    public void hidePopupPN(ActionEvent actionEvent) {
        hidePopupAnimated(playNowPopupOverlay);
    }

    public void handleConfirmHT(ActionEvent actionEvent) {
        playButtonPress((Button) actionEvent.getSource());
        try {
            PokerWebSocketClient client = PokerWebSocketClient.getInstance();
            if (client == null) {
                System.err.println("[UI Error] Cannot create table: WebSocket client is not initialized!");
                // Kick them back to login or show an error dialog
                return;
            }

            // 1. Safely grab the typed text, fallback to prompt if empty
            String inputText = buyInInput.getText();
            if (inputText == null || inputText.trim().isEmpty()) {
                inputText = buyInInput.getPromptText();
            }

            int buyInAmount = Integer.parseInt(inputText);

            // 2. Validate against bankroll
            if (buyInAmount > GameContext.getPlayerProfile().totalBankroll()) {
                System.out.println("Buy-in Amount is invalid: Exceeds bankroll");
                return;
            }

            // 3. Construct the network payload
            ObjectNode message = objectMapper.createObjectNode();
            message.put("action", "CREATE_TABLE");
            message.put("buyIn", buyInAmount); // Send their chosen buy-in to the server

            // 4. Send it over the WebSocket!
            PokerWebSocketClient.getInstance().send(message.toString());

            System.out.println("[UI] Sent CREATE_TABLE request with buy-in: " + buyInAmount);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    public void hidePopupHT(ActionEvent actionEvent) {
        hidePopupAnimated(hostTablePopupOverlay);
    }

    public void handleConfirmJT(ActionEvent actionEvent) {
        playButtonPress((Button) actionEvent.getSource());
        try {
            String inputText = tableIdInput.getText();
            if (inputText == null || inputText.trim().isEmpty()) {
                inputText = tableIdInput.getPromptText();
            }

            String tableId = tableIdInput.getText().trim();

            if (tableId.length() != 6) {
                System.out.println("Table ID is invalid");
                return;
            }
            ObjectNode message = objectMapper.createObjectNode();
            message.put("action", "JOIN_TABLE");
            message.put("tableId", inputText);

            if (PokerWebSocketClient.getInstance() == null || !PokerWebSocketClient.getInstance().isOpen()) {
                System.err.println("[Lobby] Cannot host table: WebSocket is not connected!");

                return;
            }

            PokerWebSocketClient.getInstance().send(message.toString());

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
