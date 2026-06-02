package pokergame.client.view.controllers.table;

import javafx.animation.FadeTransition;
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
import javafx.util.Duration;
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

    @FXML private Label inviteCodeLabel;
    @FXML private Label gameStatusLabel;
    @FXML private ImageView commCard1, commCard2, commCard3, commCard4, commCard5;
    @FXML private FlowPane playersContainer;
    @FXML private Label chipsInfoLabel;
    @FXML private Button foldButton;
    @FXML private Button callButton;
    @FXML private Button raiseButton;
    @FXML private ListView<String> actionFeedList;
    @FXML private Button addBotButton;
    @FXML private Button startButton;
    @FXML private Button leaveTableButton;
    @FXML private StackPane raisePopupOverlay;
    @FXML private TextField raiseAmountInput;

    private ImageView[] communityCardViews;
    private final Map<Integer, PlayerSeatController> seatControllers = new HashMap<>();
    private final Map<Integer, Node> seatNodes = new HashMap<>();

    private GameTableAnimationEngine animationEngine;
    private TableNetworkMessageHandler messageHandler;

    @FXML
    public void initialize() {
        this.communityCardViews = new ImageView[]{commCard1, commCard2, commCard3, commCard4, commCard5};
        this.animationEngine = new GameTableAnimationEngine(this);
        this.messageHandler = new TableNetworkMessageHandler(this, animationEngine);

        logAction("Joined table view. Synchronizing seating layout...");

        Map<String, Object> cachedSnapshot = GameContext.getLastTableSnapshot();
        if (cachedSnapshot != null) {
            System.out.println("[UI Controller] Found cached table layout. Performing initial draw...");
        }

        setTurnControlsEnabled(false, 0);
        inviteCodeLabel.setText("Table ID: " + GameContext.getCurrentTableId());
    }

    @FXML
    void handleLeaveTable(ActionEvent event) {
        // 1. Tell the server we are exiting
        PokerWebSocketClient.getInstance().sendPayload(Map.of("action", "LEAVE_TABLE"));

        // 2. Kill the message handler network subscription to prevent ghost leaks
        if (messageHandler != null) {
            messageHandler.cleanup();
        }

        // 3. Command the application view window context to hop out back to the lobby
        Platform.runLater(() -> {
            SceneManager.switchScene("Lobby.fxml");
        });
    }
    @FXML
    void handleStartHand(ActionEvent actionEvent) {
        PokerWebSocketClient.getInstance().sendPayload(Map.of("action", "START_HAND"));
        setTableControlsEnabled(false);
    }
    @FXML void handleAddBot(ActionEvent event) { PokerWebSocketClient.getInstance().sendPayload(Map.of("action", "ADD_BOT")); }

    @FXML
    void handleFold(ActionEvent event) {
        PokerWebSocketClient.getInstance().sendPayload(new OutboundActionPayload("FOLD", 0));
        setTurnControlsEnabled(false, 0);
    }
    @FXML
    void handleCall(ActionEvent event) {
        PokerWebSocketClient.getInstance().sendPayload(new OutboundActionPayload("CALL", 0));
        setTurnControlsEnabled(false, 0);
    }
    @FXML void showRaisePopup(ActionEvent event) { raisePopupOverlay.setVisible(true); }
    @FXML void hideRaisePopup(ActionEvent event) { raisePopupOverlay.setVisible(false); }
    @FXML void handleConfirmRaise(ActionEvent event) { /* implementation code */ }
    @FXML void handleAllIn(ActionEvent event) { /* implementation code */ }

    public void syncSeatsLayout(List<Map<String, Object>> seatsData) {
        if (seatsData == null) return;

        // 1. Filter out only the currently active and occupied seats
        Map<Integer, Map<String, Object>> activeSnapshotMap = new TreeMap<>();
        for (Map<String, Object> seat : seatsData) {
            Integer index = (Integer) seat.get("seatIndex");
            if (index != null && seat.get("playerUsername") != null && !seat.get("playerUsername").toString().isBlank()) {
                activeSnapshotMap.put(index, seat);
            }
        }

        // 2. Build or update seat controllers based on active server slots
        for (Map.Entry<Integer, Map<String, Object>> entry : activeSnapshotMap.entrySet()) {
            int index = entry.getKey();
            Map<String, Object> data = entry.getValue();
            String username = (String) data.get("playerUsername");

            int startChips = data.get("startChips") != null ? (int) data.get("startChips") : 1000;
            int endChips = data.get("endChips") != null ? (int) data.get("endChips") : 0;
            int workingStack = (endChips > 0) ? endChips : startChips;

            String holeCards = (String) data.get("holeCards");

            PlayerSeatController controller;

            if (!seatControllers.containsKey(index)) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                    Node seatNode = loader.load();
                    controller = loader.getController();

                    seatControllers.put(index, controller);
                    seatNodes.put(index, seatNode);

                    // Pass the holeCards token cleanly into the DTO
                    HandParticipantDTO initialDto = new HandParticipantDTO(username, workingStack, holeCards);
                    controller.updateFromSnapshot(initialDto, username);

                    GameTableAnimationEngine.playSeatEntryAnimation(controller);

                } catch (IOException e) {
                    System.err.println("[UI Error] Failed to inflate player seat template: " + e.getMessage());
                }
            } else {
                controller = seatControllers.get(index);

                int previousChips = controller.getCurrentChips();
                if (previousChips != 0 && previousChips != workingStack) {
                    controller.updateChips(workingStack);
                    GameTableAnimationEngine.playChipPulse(chipsInfoLabel, workingStack > previousChips);
                } else {
                    controller.updateChips(workingStack);
                }

                // Pass the holeCards token cleanly into the DTO
                HandParticipantDTO syncDto = new HandParticipantDTO(username, workingStack, holeCards);
                controller.updateFromSnapshot(syncDto, username);
            }
        }

        // 3. Clear out data trackers for seats that are no longer occupied (Left table / Disconnected)
        seatControllers.keySet().removeIf(index -> !activeSnapshotMap.containsKey(index));
        seatNodes.keySet().removeIf(index -> !activeSnapshotMap.containsKey(index));

        // 4. Repopulate JavaFX viewport layout container linearly to preserve seating indexing positions
        playersContainer.getChildren().clear();
        for (int i = 0; i <= 5; i++) {
            if (seatNodes.containsKey(i)) {
                playersContainer.getChildren().add(seatNodes.get(i));
            }
        }
    }

    public void updateCommunityCards(List<Card> activeCommunityCards) {
        // CRITICAL: Forces the UI update onto the JavaFX main thread
        Platform.runLater(() -> {
            // Case 1: Clear the entire board if the list is null or empty
            if (activeCommunityCards == null || activeCommunityCards.isEmpty()) {
                for (ImageView view : communityCardViews) {
                    if (view != null) view.setImage(null);
                }
                return;
            }

            int incomingSize = activeCommunityCards.size();

            // Case 2: Absolute Board States (Flop = 3, or Snapshot Reconnections = 4 or 5)
            if (incomingSize >= 3) {
                for (int i = 0; i < communityCardViews.length; i++) {
                    if (i < incomingSize) {
                        setCardImage(communityCardViews[i], activeCommunityCards.get(i));
                    } else {
                        communityCardViews[i].setImage(null); // Clear unused remaining slots
                    }
                }
            }
            // Case 3: Incremental Board States (Turn = 1 card, River = 1 card)
            else if (incomingSize == 1) {
                Card newCard = activeCommunityCards.get(0);
                int nextOpenSlot = -1;

                // Find the first ImageView slot that is currently empty
                for (int i = 0; i < communityCardViews.length; i++) {
                    if (communityCardViews[i].getImage() == null) {
                        nextOpenSlot = i;
                        break;
                    }
                }

                // If an open slot was found (should be index 3 for Turn, index 4 for River)
                if (nextOpenSlot != -1) {
                    setCardImage(communityCardViews[nextOpenSlot], newCard);
                } else {
                    System.err.println("[UI Warning] Received a community card but all 5 slots are already filled.");
                }
            }
        });
    }

    private void setCardImage(ImageView imageView, Card card) {
        if (imageView == null || card == null) return;

        String imagePath = CardParser.getCardImagePath(card);
        try {
            Image cardImage = new Image(getClass().getResourceAsStream(imagePath));
            imageView.setImage(cardImage);
        } catch (Exception e) {
            System.err.println("Could not load image at path: " + imagePath);
        }
    }
    public void setTurnControlsEnabled(boolean enable, int amountToCall) {
        foldButton.setDisable(!enable);
        callButton.setDisable(!enable);
        raiseButton.setDisable(!enable);

        if (enable) {
            callButton.setText(amountToCall > 0 ? "Call $" + amountToCall : "Check");
        } else {
            callButton.setText("Call");
        }
    }
    public void setAdminControlsDisabled(boolean disable) {
        addBotButton.setDisable(disable); // Stops players from breaking state loops mid-hand
    }
    public void setTableControlsEnabled(boolean enable) {
        addBotButton.setDisable(!enable);
        startButton.setDisable(!enable);
    }
    public void updateChipsAndPotDisplay(int balance, int totalPot) {
        chipsInfoLabel.setText("Your Chips: $" + balance + "  |  Pot: $" + totalPot);
    }
    public void logAction(String summary) {
        actionFeedList.getItems().add(summary);
        actionFeedList.scrollTo(actionFeedList.getItems().size() - 1);
    }
    public void setGameStatus(String status) {
        gameStatusLabel.setText(status);
    }
    public PlayerSeatController getSeatController(int seatIndex) {
        return seatControllers.get(seatIndex);
    }
}