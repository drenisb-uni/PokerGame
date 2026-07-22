package pokergame.client.view.controllers.table;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import pokergame.GameContext;
import pokergame.client.network.OutboundActionPayload;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.network.TableNetworkMessageHandler;
import pokergame.client.utils.CardParser;
import pokergame.client.view.SceneManager;
import pokergame.client.view.components.GameTableAnimationEngine;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;

import java.io.IOException;
import java.util.*;

public class GameController {

    // =================================================================================
    // FXML UI ELEMENTS (Bound to your exact references)
    // =================================================================================
    @FXML private Label inviteCodeLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Label chipsInfoLabel;
    @FXML private ImageView commCard1, commCard2, commCard3, commCard4, commCard5;
    @FXML private FlowPane playersContainer;
    @FXML private StackPane raisePopupOverlay;

    @FXML private Button foldButton, callButton, raiseButton;
    @FXML private Button addBotButton, startButton, leaveButton;
    @FXML private TextField raiseAmountField;
    @FXML private ListView<String> actionFeedList;

    // =================================================================================
    // CORE ARCHITECTURE COMPONENTS
    // =================================================================================
    private TableNetworkMessageHandler networkHandler;
    private GameTableAnimationEngine animationEngine;

    // TRACKING STATE: This map is the secret to Delta-Updates. We never rebuild the layout mid-game.
    private final Map<String, PlayerSeatController> activeSeatControllers = new HashMap<>();
    private ImageView[] communityCardViews;
    private int currentPotSize = 0;
    private int myCurrentBalance = 0;
    private boolean raisePopup = false;


    @FXML
    public void initialize() {
        // 1. Group community cards for easy iteration
        communityCardViews = new ImageView[]{commCard1, commCard2, commCard3, commCard4, commCard5};

        // 2. Setup Animation Engine to handle visual queuing
        this.animationEngine = new GameTableAnimationEngine(this);

        // 3. Initialize our new Delta-Based Network Handler
        this.networkHandler = new TableNetworkMessageHandler(this, animationEngine);

        // 4. Default UI State
        setTurnControlsEnabled(false, 0);
        resetCommunityCards();

        String tableId = GameContext.getCurrentTableId();
        if (tableId != null) {
            inviteCodeLabel.setText("Table ID: " + tableId);
        }
    }
    // =================================================================================
    // DELTA-BASED UI UPDATE METHODS (Triggered by NetworkMessageHandler)
    // =================================================================================


    public void buildInitialTableLayout(List<Map<String, Object>> seatsData) {
        playersContainer.getChildren().clear();
        activeSeatControllers.clear();

        for (Map<String, Object> seatMap : seatsData) {
            if (seatMap == null || seatMap.isEmpty()) continue; // Use continue, NOT return!

            HandParticipantDTO dto = mapToParticipantDTO(seatMap);
            if (dto.playerUsername() == null) {
                continue;
            }
            try {
                // Load the FXML ONLY for seats that actually have a player
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                Node seatNode = loader.load();
                PlayerSeatController seatController = loader.getController();

                String clientUsername = GameContext.getUsername();

                seatController.updateFromSnapshot(dto, clientUsername);

                playersContainer.getChildren().add(seatNode);

                // Cache the controller reference by Username for surgical O(1) delta updates later
                activeSeatControllers.put(dto.playerUsername(), seatController);

                // Safe to use .equals() now because we guaranteed playerUsername is not null
                if (dto.playerUsername().equals(clientUsername)) {
                    this.myCurrentBalance = dto.startChips();
                    updateChipsAndPotDisplay();
                }
            } catch (IOException e) {
                System.err.println("[UI Setup Error] Failed to load PlayerSeat.fxml: " + e.getMessage());
            }
        }
    }
    public void updatePlayerHoleCards(String username, String cardsToken) {
        PlayerSeatController seat = activeSeatControllers.get(username);
        if (seat != null) {
            seat.setHoleCards(cardsToken);
        }
    }

    public void updatePotSize(int newTotal) {
        this.currentPotSize = newTotal;
        updateChipsAndPotDisplay();
    }

    public void updatePlayerChips(String username, int newBalance) {
        PlayerSeatController seat = activeSeatControllers.get(username);
        if (seat != null) {
            seat.updateChips(newBalance);

            if (username.equals(GameContext.getInstance().getUsername())) {
                this.myCurrentBalance = newBalance;
                updateChipsAndPotDisplay();
            }
        }
    }

    public void updateCommunityCards(List<Card> cards) {
        int emptySlotIndex = 0;
        while (emptySlotIndex < communityCardViews.length && communityCardViews[emptySlotIndex].getImage() != null) {
            emptySlotIndex++;
        }
        for (Card card : cards) {
            if (emptySlotIndex < communityCardViews.length) {
                setCardImage(communityCardViews[emptySlotIndex], CardParser.getCardImagePath(card));
                emptySlotIndex++;
            }
        }
    }

    public void resetCommunityCards() {
        for (ImageView view : communityCardViews) {
            view.setImage(null);
        }
    }

    // =================================================================================
    // UI CONTROL STATE METHODS
    // =================================================================================

    public void setTurnControlsEnabled(boolean enable, int amountToCall) {
        foldButton.setDisable(!enable);
        callButton.setDisable(!enable);
        raiseButton.setDisable(!enable);
        if(raiseAmountField != null) raiseAmountField.setDisable(!enable);

        if (enable) {
            callButton.setText(amountToCall > 0 ? "Call $" + amountToCall : "Check");
        } else {
            callButton.setText("Call");
        }
    }

    public void setAdminControlsDisabled(boolean disable) {
        addBotButton.setDisable(disable);
    }

    public void setTableControlsEnabled(boolean enable) {
        addBotButton.setDisable(!enable);
        startButton.setDisable(!enable);
    }

    public void setGameStatus(String status) {
        gameStatusLabel.setText(status);
    }

    private void updateChipsAndPotDisplay() {
        chipsInfoLabel.setText("Your Chips: $" + myCurrentBalance + "  |  Pot: $" + currentPotSize);
    }

    public void logActionFeed(String summary) {
        actionFeedList.getItems().add(summary);
        actionFeedList.scrollTo(actionFeedList.getItems().size() - 1);
    }

    // =================================================================================
    // OUTBOUND USER ACTION HANDLERS (BUTTON CLICKS)
    // =================================================================================
    @FXML
    private void handleFold(ActionEvent event) {
        sendPlayerAction("FOLD", 0);
        setTurnControlsEnabled(false, 0);
    }
    @FXML
    private void handleCall(ActionEvent event) {
        sendPlayerAction("CALL", 0);
        setTurnControlsEnabled(false, 0);
    }
    @FXML
    private void handleRaise(ActionEvent event) {

    }
    @FXML
    public void handleRaisePopup(ActionEvent event) {
        raisePopup = !raisePopup;
        raisePopupOverlay.setDisable(!raisePopup);
    }
    @FXML
    public void handleAllIn(ActionEvent event) {
    }
    @FXML
    private void handleStartHand(ActionEvent event) {
        OutboundActionPayload payload = new OutboundActionPayload("START_HAND", 0);
        // Ensure you have an accessible instance getter or replace with your standard dispatch
        PokerWebSocketClient.getInstance().sendPayload(payload);
    }
    @FXML
    private void handleAddBot(ActionEvent event) {
        OutboundActionPayload payload = new OutboundActionPayload("ADD_BOT", 0);
        PokerWebSocketClient.getInstance().sendPayload(payload);
    }
    @FXML
    private void handleLeaveTable(ActionEvent event) {
        networkHandler.cleanup(); // Clean up EventBus listeners!
        OutboundActionPayload payload = new OutboundActionPayload("LEAVE_TABLE", 0);
        PokerWebSocketClient.getInstance().sendPayload(payload);
        SceneManager.switchScene("/fxml/Lobby.fxml");
    }

    // =================================================================================
    // UTILITY HELPERS
    // =================================================================================

    private void sendPlayerAction(String actionType, int amount) {
        OutboundActionPayload payload = new OutboundActionPayload(actionType, amount);
        PokerWebSocketClient.getInstance().sendPayload(payload);
    }

    private void setCardImage(ImageView imageView, String imagePath) {
        try {
            Image cardImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imagePath)));
            imageView.setImage(cardImage);
        } catch (Exception e) {
            System.err.println("Could not load image at path: " + imagePath);
        }
    }

    public PlayerSeatController getSeatControllerByUsername(String username) {
        return activeSeatControllers.get(username);
    }

    /**
     * Converts a raw map from Jackson into the DTO expected by PlayerSeatController
     */
    private HandParticipantDTO mapToParticipantDTO(Map<String, Object> map) {
        return new HandParticipantDTO(
                (String) map.get("playerUsername"),
                (int) map.getOrDefault("startChips", 0),
                (int) map.getOrDefault("endChips", 0),
                map.containsKey("holeCards") ? map.get("holeCards").toString() : "HIDDEN",
                (boolean) map.getOrDefault("hasFolded", false),
                (boolean) map.getOrDefault("isAllIn", false)
        );
    }
}