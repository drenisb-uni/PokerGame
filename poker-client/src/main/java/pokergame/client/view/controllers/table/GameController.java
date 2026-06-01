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
import pokergame.client.view.SceneManager;
import pokergame.client.view.components.GameTableAnimationEngine;
import pokergame.domain.model.Card;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

        inviteCodeLabel.setText("Table ID: " + GameContext.getCurrentTableId());
    }

    public PlayerSeatController getSeatController(int seatIndex) {
        return seatControllers.get(seatIndex);
    }

    public void setGameStatus(String status) {
        gameStatusLabel.setText(status);
    }

    public void updateChipsAndPotDisplay(int balance, int totalPot) {
        chipsInfoLabel.setText("Your Chips: $" + balance + "  |  Pot: $" + totalPot);
    }

    public void logAction(String summary) {
        actionFeedList.getItems().add(summary);
        actionFeedList.scrollTo(actionFeedList.getItems().size() - 1);
    }

    public void setAdminControlsDisabled(boolean disable) {
        addBotButton.setDisable(disable); // Stops players from breaking state loops mid-hand
    }

    public void syncSeatsLayout(List<Map<String, Object>> seatsData) {
        // 1. Identify which seats are currently occupied based on username presence
        Map<Integer, Map<String, Object>> activeSnapshotMap = new TreeMap<>();
        for (Map<String, Object> seat : seatsData) {
            Integer index = (Integer) seat.get("seatIndex");
            if (seat.get("playerUsername") != null && !seat.get("playerUsername").toString().isBlank()) {
                activeSnapshotMap.put(index, seat);
            }
        }

        // 2. Build or update controllers for active slots
        for (Map.Entry<Integer, Map<String, Object>> entry : activeSnapshotMap.entrySet()) {
            int index = entry.getKey();
            Map<String, Object> data = entry.getValue();
            String username = (String) data.get("playerUsername");

            // FIX: Safe stack evaluation. If endChips isn't populated or is 0, default to startChips
            int startChips = data.get("startChips") != null ? (int) data.get("startChips") : 1000;
            int endChips = data.get("endChips") != null ? (int) data.get("endChips") : 0;
            int workingStack = (endChips > 0) ? endChips : startChips;

            if (!seatControllers.containsKey(index)) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                    Node seatNode = loader.load();
                    PlayerSeatController controller = loader.getController();

                    seatControllers.put(index, controller);
                    seatNodes.put(index, seatNode);

                    pokergame.domain.dto.HandParticipantDTO pseudoDto =
                            new pokergame.domain.dto.HandParticipantDTO(username, workingStack);
                    controller.setup(pseudoDto);
                    controller.playSeatEntryAnimation();

                } catch (IOException e) {
                    System.err.println("[UI Error] Failed to inflate player seat template: " + e.getMessage());
                }
            } else {
                // Update stack metrics on an existing seat
                seatControllers.get(index).updateChips(workingStack);
                String currentAction = (String) data.get("lastAction");
                if (currentAction != null && !currentAction.isBlank()) {
                    seatControllers.get(index).restoreActionVisual(currentAction);
                }
            }
        }

        // 3. Clear out any seats that are no longer occupied
        seatControllers.keySet().removeIf(index -> !activeSnapshotMap.containsKey(index));
        seatNodes.keySet().removeIf(index -> !activeSnapshotMap.containsKey(index));

        // 4. Clear layout and push remaining nodes back linearly to preserve indexing
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
            for (int i = 0; i < communityCardViews.length; i++) {
                if (activeCommunityCards != null && i < activeCommunityCards.size()) {
                    // Card is dealt! Load its specific image
                    Card card = activeCommunityCards.get(i);
                    String imagePath = getCardImagePath(card);

                    try {
                        Image cardImage = new Image(getClass().getResourceAsStream(imagePath));
                        communityCardViews[i].setImage(cardImage);
                    } catch (Exception e) {
                        System.err.println("Could not load image at path: " + imagePath);
                    }
                } else {
                    // Card not dealt yet: Show card back or clear it entirely
                    // Option A: communityCardViews[i].setImage(cardBackImage);
                    // Option B: clear it
                    communityCardViews[i].setImage(null);
                }
            }
        });
    }

    private String getCardImagePath(Card card) {
        String suitStr = card.getSuit().substring(0, 1).toUpperCase();
        String valStr = switch (card.getValue()) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(card.getValue());
        };
        return "/images/" + valStr + "-" + suitStr + ".png";
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


    public void setTableControlsEnabled(boolean enable) {
        addBotButton.setDisable(!enable);
        startButton.setDisable(!enable);
    }
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
    @FXML
    void handleStartHand(ActionEvent actionEvent) {
        PokerWebSocketClient.getInstance().sendPayload(Map.of("action", "START_HAND"));
        setTableControlsEnabled(false);
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

    @FXML void handleAddBot(ActionEvent event) { PokerWebSocketClient.getInstance().sendPayload(Map.of("action", "ADD_BOT")); }
    @FXML void showRaisePopup(ActionEvent event) { raisePopupOverlay.setVisible(true); }
    @FXML void hideRaisePopup(ActionEvent event) { raisePopupOverlay.setVisible(false); }
    @FXML void handleConfirmRaise(ActionEvent event) { /* implementation code */ }
    @FXML void handleAllIn(ActionEvent event) { /* implementation code */ }

}