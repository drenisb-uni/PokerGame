package pokergame.client.view.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import pokergame.GameContext;
import pokergame.client.network.OutboundActionPayload;
import pokergame.client.network.PokerWebSocketClient;
import pokergame.client.utils.EventBus;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.GameMessageDTO;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.IPublicActionAPI; // Decoupled interface contract

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class GameController implements IGameEventListener {

    // --- FXML INJECTIONS ---
    @FXML private ImageView commCard1, commCard2, commCard3, commCard4, commCard5;
    private ImageView[] communityCards;
    @FXML private FlowPane playersContainer;
    @FXML private Label chipsInfoLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Button foldButton;
    @FXML private Button callButton;
    @FXML private Button raiseButton;
    @FXML private Button addBotButton;
    @FXML private Button leaveTableButton;
    @FXML private ListView<String> actionFeedList;

    // Raise Popup Elements
    @FXML private StackPane raisePopupOverlay;
    @FXML private TextField raiseAmountInput;

    // --- DECOUPLED NETWORK STATE ---
    private IPublicActionAPI actionAPI; // Bound directly to your WebSocketClientAPI
    private String localUsername;       // Cached locally from MainApp injection context

    // --- GAME STATE VARIABLES ---
    private final Map<String, PlayerSeatController> seatControllerMap = new HashMap<>();
    private final Map<String, String> latestPlayerActions = new HashMap<>();
    private final Queue<HandActionDTO> pendingActionAnimations = new ArrayDeque<>();
    private static final int WINNER_SCREEN_HOLD_MS = 8000;
    private boolean actionAnimationRunning = false;
    private boolean winnerAnimationRunning = false;
    private boolean applyingDeferredTableSetup = false;
    private PauseTransition winnerDisplayHold;
    private String lastAnimatedActionPlayer = null;
    private Map<String, Object> deferredTableSnapshot;
    private GameMessageDTO deferredHandResultMessage;
    private List<String> activeWinnerUsernames = List.of();
    private List<String> deferredWinnerUsernames;
    private HandResult deferredWinnerHand;
    private int deferredWinnerPotSize;
    private boolean hasDeferredHandResult = false;
    private GameState deferredGameState;
    private boolean hasDeferredGameState = false;
    private String deferredTurnUsername;
    private int deferredTurnAmountToCall;
    private boolean hasDeferredTurnPrompt = false;
    private int currentCommunityCardIndex = 0;
    private int currentAmountToCall = 0;
    private int localPotSize = 0;
    private int activeWinnerPotSize = 0;
    private int maxSeatsAtTable = 6;

    @FXML
    public void initialize() {
        communityCards = new ImageView[]{ commCard1, commCard2, commCard3, commCard4, commCard5 };
        raisePopupOverlay.setVisible(false);
        disableBettingControls(); // Stay safe until the server prompts us for our turn

        EventBus.subscribe("DEAL_CARDS", this::handleDealCards);
        EventBus.subscribe("PLAYER_FOLDED", this::handlePlayerFold);
        EventBus.subscribe("CHAT_MESSAGE", this::handleChat);
        EventBus.subscribe("TABLE_SNAPSHOT", this::handleTableSnapshot);
        EventBus.subscribe("GAME_STATE", this::handleGameStateMessage);
        EventBus.subscribe("TURN_PROMPT", this::handleTurnPrompt);
        EventBus.subscribe("COMMUNITY_CARDS", this::handleCommunityCardsMessage);
        EventBus.subscribe("PLAYER_ACTION", this::handlePlayerActionMessage);
        EventBus.subscribe("HAND_RESULT", this::handleHandResultMessage);

        if (GameContext.getPlayerProfile() != null) {
            localUsername = GameContext.getPlayerProfile().username();
        }

        Platform.runLater(() -> sendAction("REFRESH_TABLE", 0));

        System.out.println("[Table UI] Listening for game events...");
    }

    /**
     * Dependency injection hook invoked dynamically by MainApp during startup phase orchestration.
     */
    public void setLocalPlayerContext(String currentUsername, IPublicActionAPI clientAPI) {
        this.localUsername = currentUsername;
        this.actionAPI = clientAPI;
        System.out.println("[UI Controller] Network boundary contextualized for: " + localUsername);
    }

    // --- CLEAN PRODUCTION BUTTON HANDLERS ---

    @FXML
    public void handleFold(ActionEvent event) {
        playButtonPress(foldButton);
        disableBettingControls();
        sendAction("FOLD", 0);
    }

    @FXML
    public void handleCall(ActionEvent event) {
        playButtonPress(callButton);
        disableBettingControls();
        sendAction("CALL", 0);
    }

    @FXML
    public void handleConfirmRaise(ActionEvent event) {
        playButtonPress((Button) event.getSource());
        try {
            int amount = Integer.parseInt(raiseAmountInput.getText().trim());

            if (amount <= currentAmountToCall) {
                System.out.println("Raise must be greater than the current call amount!");
                return;
            }

            disableBettingControls();
            hideRaisePopupAnimated();

            sendAction("RAISE", amount);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    @FXML
    public void handleAllIn(ActionEvent event) {
        playButtonPress((Button) event.getSource());
        disableBettingControls();
        hideRaisePopupAnimated();

        // Fetch current active chip reserves straight from the UI tracker
        PlayerSeatController localSeat = seatControllerMap.get(localUsername);
        int playerTotalChips = (localSeat != null) ? localSeat.getCurrentChips() : 0;

        sendAction("RAISE", playerTotalChips);
    }

    @FXML
    public void handleAddBot(ActionEvent event) {
        playButtonPress(addBotButton);
        sendAction("ADD_BOT", 0);
    }

    @FXML
    public void handleLeaveTable(ActionEvent event) {
        playButtonPress(leaveTableButton);
        sendAction("LEAVE_TABLE", 0);
        PokerWebSocketClient client = PokerWebSocketClient.getInstance();
        if (client != null && client.isOpen()) {
            client.close();
        }
        SceneManager.switchScene("Lobby.fxml");
    }

    @FXML
    public void showRaisePopup(ActionEvent event) {
        playButtonPress(raiseButton);
        raiseAmountInput.clear();
        showRaisePopupAnimated();
    }

    @FXML
    public void hideRaisePopup(ActionEvent event) {
        playButtonPress((Button) event.getSource());
        hideRaisePopupAnimated();
    }

    // --- ENGINE EVENT LISTENERS (Inbound Server Events) ---

    @Override
    public void onGameStateChanged(GameState newState) {
        Platform.runLater(() -> {
            if (!applyingDeferredTableSetup && isVisualSequenceActive()) {
                deferredGameState = newState;
                hasDeferredGameState = true;
                disableBettingControls();
                return;
            }

            switch (newState) {
                case WAITING_FOR_PLAYERS:
                    if (gameStatusLabel != null) gameStatusLabel.setText("Waiting for players...");
                    break;
                case PRE_FLOP_BETTING:
                    latestPlayerActions.clear();
                    if (actionFeedList != null) {
                        actionFeedList.getItems().clear();
                    }
                    if (gameStatusLabel != null) gameStatusLabel.setText("Pre-flop");
                    currentCommunityCardIndex = 0;
                    for (ImageView iv : communityCards) {
                        iv.setImage(null);
                    }
                    break;
                case FLOP_BETTING:
                    if (gameStatusLabel != null) gameStatusLabel.setText("Flop");
                    break;
                case TURN_BETTING:
                    if (gameStatusLabel != null) gameStatusLabel.setText("Turn");
                    break;
                case RIVER_BETTING:
                    if (gameStatusLabel != null) gameStatusLabel.setText("River");
                    break;
                case HAND_OVER:
                    if (gameStatusLabel != null) gameStatusLabel.setText("Round over - add bots now");
                    disableBettingControls();
                    if (addBotButton != null) addBotButton.setDisable(false);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void onCommunityCardsDealt(List<Card> cards) {
        Platform.runLater(() -> {
            for (Card card : cards) {
                if (currentCommunityCardIndex < communityCards.length) {
                    String imagePath = getImagePathForCard(card);
                    try {
                        Image cardImg = new Image(getClass().getResource(imagePath).toExternalForm());
                        communityCards[currentCommunityCardIndex].setImage(cardImg);
                        playCommunityCardAnimation(communityCards[currentCommunityCardIndex], currentCommunityCardIndex);
                    } catch (Exception e) {
                        System.err.println("Could not load image: " + imagePath);
                    }
                    currentCommunityCardIndex++;
                }
            }
        });
    }

    @Override
    public void onPlayerTurn(String username, int amountToCall) {
        Platform.runLater(() -> {
            // Guard clause if data initializes before injection finishes
            if (localUsername == null) return;
            if (isVisualSequenceActive()) {
                deferredTurnUsername = username;
                deferredTurnAmountToCall = amountToCall;
                hasDeferredTurnPrompt = true;
                disableBettingControls();
                if (chipsInfoLabel != null) {
                    chipsInfoLabel.setText("Resolving player actions... | Pot: $" + this.localPotSize);
                }
                if (!actionAnimationRunning && !pendingActionAnimations.isEmpty()) {
                    playNextQueuedPlayerAction();
                }
                return;
            }

            if (username.equals(localUsername)) {
                this.currentAmountToCall = amountToCall;
                enableBettingControls();
                callButton.setText(amountToCall == 0 ? "Check" : "Call $" + amountToCall);
                playActionControlsReadyAnimation();
            } else {
                disableBettingControls();
            }

            seatControllerMap.values().forEach(controller -> controller.setTurnActive(false));
            PlayerSeatController activeController = seatControllerMap.get(username);
            if (activeController != null) {
                activeController.setTurnActive(true);
            }

            PlayerSeatController controller = seatControllerMap.get(localUsername);
            int currentChips = (controller != null) ? controller.getCurrentChips() : 0;

            if (username.equals(localUsername)) {
                updateChipsDisplay(currentChips, this.localPotSize);
            } else {
                chipsInfoLabel.setText("Waiting for " + username + " to act... | Pot: $" + this.localPotSize);
            }
        });
    }

    @Override
    public void onPlayerAction(HandActionDTO action) {
        Platform.runLater(() -> {
            pendingActionAnimations.add(action);
            if (winnerAnimationRunning || applyingDeferredTableSetup) {
                disableBettingControls();
                return;
            }
            playNextQueuedPlayerAction();
        });
    }

    @Override
    public void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize) {
        Platform.runLater(() -> {
            if (isActionSequenceActive()) {
                deferredWinnerUsernames = winnerUsernames == null ? List.of() : new ArrayList<>(winnerUsernames);
                deferredWinnerHand = winnerHand;
                deferredWinnerPotSize = potSize;
                hasDeferredHandResult = true;
                disableBettingControls();
                return;
            }

            List<String> safeWinnerUsernames = winnerUsernames == null ? List.of() : winnerUsernames;
            StringBuilder winMsg = new StringBuilder();
            for (String username : safeWinnerUsernames) {
                winMsg.append(username).append(" ");
            }

            String handTypeStr = (winnerHand != null && winnerHand.getType() != null)
                    ? winnerHand.getType().toString().replace("_", " ")
                    : "Muck";

            winMsg.append("won $").append(potSize).append(" with ").append(handTypeStr);
            chipsInfoLabel.setText(winMsg.toString());
            openAddBotWindow();
            appendActionFeed(winMsg.toString());
            playHandResultAnimation(safeWinnerUsernames, potSize);
            this.localPotSize = 0;
        });
    }

    @Override
    public void onTableSnapshotBroadcast(Map<String, Object> snapshotPayload) {
        Platform.runLater(() -> handleTableSnapshot(snapshotPayload));
    }

    @Override
    public void onTargetedTableSnapshot(String playerId, Map<String, Object> snapshotPayload) {
        String myId = GameContext.getPlayerProfile().id();
        if (myId != null && myId.equals(playerId)) {
            Platform.runLater(() -> handleTableSnapshot(snapshotPayload));
        }
    }

    @Override
    public void onNewSeatOccupied(HandParticipantDTO participant) {
        Platform.runLater(() -> {
            if (seatControllerMap.containsKey(participant.playerUsername())) {
                // If seat already populated locally, refresh current counts instead of inflating layouts
                PlayerSeatController existingCtrl = seatControllerMap.get(participant.playerUsername());
                existingCtrl.setup(participant);
                if (latestPlayerActions.containsKey(participant.playerUsername())) {
                    existingCtrl.restoreActionVisual(latestPlayerActions.get(participant.playerUsername()));
                }
                return;
            }

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                VBox seatUI = loader.load();

                PlayerSeatController controller = loader.getController();
                controller.setup(participant);
                boolean hasLatestAction = latestPlayerActions.containsKey(participant.playerUsername());
                if (hasLatestAction) {
                    controller.restoreActionVisual(latestPlayerActions.get(participant.playerUsername()));
                }

                if (participant.playerUsername().equals(localUsername) && !"HIDDEN".equals(participant.holeCards())) {
                    List<Card> cards = parseCardsString(participant.holeCards());
                    if (cards.size() == 2) {
                        controller.revealCards(cards.get(0), cards.get(1));
                    }
                }

                playersContainer.getChildren().add(seatUI);
                seatControllerMap.put(participant.playerUsername(), controller);
                if (!hasLatestAction) {
                    controller.playSeatEntryAnimation();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // --- Event Handlers ---

    private void handleDealCards(GameMessageDTO event) {
        System.out.println("[Table UI] Rendering cards from server: " + event.payload());
        // TODO: Cast the payload to a List and update JavaFX ImageViews
    }

    private void handlePlayerFold(GameMessageDTO event) {
        //String foldingPlayer = event.sender();
        System.out.println("[Table UI] Grey out avatar for: ");
        // TODO: Update UI
    }

    private void handleChat(GameMessageDTO event) {
        // TODO: Append event.payload() to the chat box UI
    }

    private void handleTableSnapshot(Map<String, Object> payload) {
        Platform.runLater(() -> {
            System.out.println("--- [UI DEBUG] applyTableSnapshot triggered! ---");

            if (payload == null) {
                System.err.println("[UI DEBUG] Payload is NULL! Aborting.");
                return;
            }

            String gameState = asString(payload.get("gameState"));

            if (!applyingDeferredTableSetup && isVisualSequenceActive()
                    && !(winnerAnimationRunning && "HAND_OVER".equals(gameState))) {

                System.out.println("[UI DEBUG] SNAPSHOT BLOCKED! Deferring because animations are active!");
                System.out.println("actionAnimationRunning: " + actionAnimationRunning);
                System.out.println("pendingActionAnimations count: " + pendingActionAnimations.size());

                deferredTableSnapshot = payload;
                if (!actionAnimationRunning && !pendingActionAnimations.isEmpty()) {
                    playNextQueuedPlayerAction();
                }
                return;
            }

            List<?> seats = asList(payload.get("seats"));
            System.out.println("[UI DEBUG] Attempting to render " + (seats != null ? seats.size() : 0) + " seats.");            int maxSeats = asInt(payload.get("maxSeats"), 6);
            this.maxSeatsAtTable = maxSeats;
            int potSize = asInt(payload.get("potSize"), this.localPotSize);
            int tableBuyIn = asInt(payload.get("tableBuyIn"), 0);
            int smallBlind = asInt(payload.get("smallBlind"), 0);
            int bigBlind = asInt(payload.get("bigBlind"), 0);

            playersContainer.getChildren().clear();
            seatControllerMap.clear();
            this.localPotSize = potSize;

            try {
                if (seats != null) {
                    for (Object seatPayload : seats) {
                        Map<String, Object> seatMap = asMap(seatPayload);
                        HandParticipantDTO participant = new HandParticipantDTO(
                                asString(seatMap.get("handId")),
                                asString(seatMap.get("playerUsername")),
                                asInt(seatMap.get("seatIndex"), 0),
                                asString(seatMap.get("holeCards")),
                                asInt(seatMap.get("startChips"), 0),
                                asInt(seatMap.get("endChips"), 0),
                                asInt(seatMap.get("netProfit"), 0),
                                Boolean.TRUE.equals(seatMap.get("winner")) || Boolean.TRUE.equals(seatMap.get("isWinner"))
                        );
                        renderSeat(participant);
                    }
                }
            } catch (Exception e) {
                System.err.println("[UI FATAL] Crash while rendering seats!");
                e.printStackTrace();
            }



            if (addBotButton != null) {
                boolean handInProgress = !gameState.isBlank()
                        && !"WAITING_FOR_PLAYERS".equals(gameState)
                        && !"HAND_OVER".equals(gameState);
                addBotButton.setDisable(seats.size() >= maxSeats || handInProgress);
            }

            if (gameStatusLabel != null) {
                if (seats.size() < 2) {
                    gameStatusLabel.setText(tableLabel(tableBuyIn, smallBlind, bigBlind) + " | Waiting for players...");
                } else if ("HAND_OVER".equals(gameState)) {
                    gameStatusLabel.setText("Round over - add bots now");
                } else if (!gameState.isBlank()) {
                    gameStatusLabel.setText(tableLabel(tableBuyIn, smallBlind, bigBlind));
                }
            }

            if (!"HAND_OVER".equals(gameState)) {
                chipsInfoLabel.setText("Your Chips: $" + getLocalChipCount() + "  |  Pot: $" + this.localPotSize);
            } else if (winnerAnimationRunning) {
                restoreWinnerDisplayAfterTableRefresh();
            }
        });
    }

    private void handleTableSnapshot(GameMessageDTO event) {
        Map<String, Object> payload = asMap(event.payload());
        handleTableSnapshot(payload);
    }

    private void handleGameStateMessage(GameMessageDTO event) {
        try {
            GameState state = GameState.valueOf(asString(event.payload()));
            if (state == GameState.PRE_FLOP_BETTING && !isVisualSequenceActive()) {
                latestPlayerActions.clear();
                if (actionFeedList != null) {
                    actionFeedList.getItems().clear();
                }
            }
            onGameStateChanged(state);
        } catch (Exception e) {
            System.err.println("Could not read game state update: " + event.payload());
        }
    }

    private void handleTurnPrompt(GameMessageDTO event) {
        Map<String, Object> payload = asMap(event.payload());
        onPlayerTurn(asString(payload.get("username")), asInt(payload.get("amountToCall"), 0));
    }

    private void handleCommunityCardsMessage(GameMessageDTO event) {
        List<Card> cards = new ArrayList<>();
        for (Object item : asList(event.payload())) {
            Map<String, Object> cardMap = asMap(item);
            cards.add(new Card(asInt(cardMap.get("value"), 0), asString(cardMap.get("suit"))));
        }
        onCommunityCardsDealt(cards);
    }

    private void handlePlayerActionMessage(GameMessageDTO event) {
        Map<String, Object> payload = asMap(event.payload());
        onPlayerAction(new HandActionDTO(
                asInt(payload.get("id"), 0),
                asString(payload.get("handId")),
                asString(payload.get("playerId")),
                asString(payload.get("roundStage")),
                asInt(payload.get("sequenceNumber"), 0),
                asString(payload.get("actionType")),
                asInt(payload.get("amount"), 0)
        ));
    }

    private void handleHandResultMessage(GameMessageDTO event) {
        Platform.runLater(() -> {
            if (isActionSequenceActive()) {
                deferredHandResultMessage = event;
                disableBettingControls();
                return;
            }

            Map<String, Object> payload = asMap(event.payload());
            String winners = String.join(" ", asStringList(payload.get("winnerUsernames")));
            String hand = asString(payload.get("winnerHand"));
            int potSize = asInt(payload.get("potSize"), 0);
            chipsInfoLabel.setText(winners + " won $" + potSize + " with " + (hand.isBlank() ? "Muck" : hand.replace("_", " ")));
            openAddBotWindow();
            appendActionFeed(winners + " won $" + potSize);
            playHandResultAnimation(asStringList(payload.get("winnerUsernames")), potSize);
            localPotSize = 0;
        });
    }
    private void renderSeat(HandParticipantDTO participant) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
            VBox seatUI = loader.load();
            PlayerSeatController controller = loader.getController();

            // --- IDENTITY FIX: Swap UUID for Display Name ---
            String technicalId = participant.playerUsername(); // e.g., "e8ea8157..." or "Bot_1"
            String displayName = technicalId;

            // If this seat belongs to the logged-in player, show their real username ("qqq")
            if (GameContext.getPlayerProfile() != null && technicalId.equals(GameContext.getPlayerProfile().id())) {
                displayName = GameContext.getPlayerProfile().username();
            }

            // Pass the resolved display name to the controller
            // (You may need to update setup() to accept this string if it only took the DTO before)
            controller.setup(participant, displayName);

            // --- ANIMATION & ACTION RESTORATION ---
            boolean hasLatestAction = latestPlayerActions.containsKey(technicalId);
            if (hasLatestAction) {
                controller.restoreActionVisual(latestPlayerActions.get(technicalId));
            }

            List<Card> visibleCards = parseCardsString(participant.holeCards());
            if (visibleCards.size() == 2) {
                controller.revealCards(visibleCards.get(0), visibleCards.get(1));
            }

            // --- MOUNT TO UI ---
            playersContainer.getChildren().add(seatUI);

            // CRITICAL: Always use the technicalId (UUID/Bot_X) for maps so future actions match!
            seatControllerMap.put(technicalId, controller);

            if (!hasLatestAction) {
                controller.playSeatEntryAnimation();
            }
        } catch (IOException e) {
            System.err.println("Failed to render player seat: " + e.getMessage());
            e.printStackTrace();
        }
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

    private void showRaisePopupAnimated() {
        if (raisePopupOverlay == null) {
            return;
        }

        raisePopupOverlay.setOpacity(0);
        raisePopupOverlay.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(320), raisePopupOverlay);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void hideRaisePopupAnimated() {
        if (raisePopupOverlay == null || !raisePopupOverlay.isVisible()) {
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), raisePopupOverlay);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            raisePopupOverlay.setVisible(false);
            raisePopupOverlay.setOpacity(1);
        });
        fadeOut.play();
    }

    private void playActionControlsReadyAnimation() {
        playButtonReadyAnimation(foldButton, 0);
        playButtonReadyAnimation(callButton, 170);
        playButtonReadyAnimation(raiseButton, 340);
    }

    private void playButtonReadyAnimation(Button button, int delayMillis) {
        if (button == null) {
            return;
        }

        PauseTransition delay = new PauseTransition(Duration.millis(delayMillis));
        ScaleTransition pop = new ScaleTransition(Duration.millis(230), button);
        pop.setToX(1.07);
        pop.setToY(1.07);

        ScaleTransition settle = new ScaleTransition(Duration.millis(230), button);
        settle.setToX(1);
        settle.setToY(1);

        delay.setOnFinished(event -> new SequentialTransition(pop, settle).play());
        delay.play();
    }

    private void playCommunityCardAnimation(ImageView cardView, int cardIndex) {
        if (cardView == null) {
            return;
        }

        cardView.setOpacity(0);
        cardView.setScaleX(0.68);
        cardView.setScaleY(0.68);
        cardView.setTranslateY(-38);
        cardView.setRotate(-8);

        PauseTransition delay = new PauseTransition(Duration.millis(cardIndex * 240L));
        FadeTransition fadeIn = new FadeTransition(Duration.millis(380), cardView);
        fadeIn.setToValue(1);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(460), cardView);
        scaleIn.setToX(1);
        scaleIn.setToY(1);

        TranslateTransition slideDown = new TranslateTransition(Duration.millis(460), cardView);
        slideDown.setToY(0);

        ParallelTransition reveal = new ParallelTransition(fadeIn, scaleIn, slideDown);
        reveal.setOnFinished(event -> cardView.setRotate(0));
        delay.setOnFinished(event -> reveal.play());
        delay.play();
    }

    private void playPotPulse() {
        if (chipsInfoLabel == null) {
            return;
        }

        chipsInfoLabel.getStyleClass().remove("pot-pulse");
        chipsInfoLabel.getStyleClass().add("pot-pulse");

        ScaleTransition grow = new ScaleTransition(Duration.millis(260), chipsInfoLabel);
        grow.setToX(1.09);
        grow.setToY(1.09);

        ScaleTransition settle = new ScaleTransition(Duration.millis(300), chipsInfoLabel);
        settle.setToX(1);
        settle.setToY(1);

        PauseTransition cleanup = new PauseTransition(Duration.millis(700));
        cleanup.setOnFinished(event -> chipsInfoLabel.getStyleClass().remove("pot-pulse"));

        new SequentialTransition(grow, settle, cleanup).play();
    }

    private void playHandResultAnimation(List<String> winnerUsernames, int potSize) {
        activeWinnerUsernames = winnerUsernames == null ? List.of() : new ArrayList<>(winnerUsernames);
        activeWinnerPotSize = potSize;
        startWinnerDisplayHold();
        playPotPulse();
        openAddBotWindow();
        if (winnerUsernames == null) {
            return;
        }

        for (String username : winnerUsernames) {
            PlayerSeatController winnerController = seatControllerMap.get(username);
            if (winnerController != null) {
                winnerController.playWinnerAnimation(potSize);
            }
        }
    }

    private void openAddBotWindow() {
        if (gameStatusLabel != null) {
            gameStatusLabel.setText("Winner shown - add bots now");
        }
        if (addBotButton != null) {
            addBotButton.setDisable(seatControllerMap.size() >= maxSeatsAtTable);
        }
    }

    private void restoreWinnerDisplayAfterTableRefresh() {
        openAddBotWindow();
        if (activeWinnerUsernames.isEmpty()) {
            return;
        }

        playPotPulse();
        for (String username : activeWinnerUsernames) {
            PlayerSeatController winnerController = seatControllerMap.get(username);
            if (winnerController != null) {
                winnerController.playWinnerAnimation(activeWinnerPotSize);
            }
        }
    }

    private void playActionFeedAnimation() {
        if (actionFeedList == null) {
            return;
        }

        actionFeedList.setOpacity(0.62);
        actionFeedList.setTranslateY(14);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(320), actionFeedList);
        fadeIn.setToValue(1);

        TranslateTransition slideUp = new TranslateTransition(Duration.millis(320), actionFeedList);
        slideUp.setToY(0);

        new ParallelTransition(fadeIn, slideUp).play();
    }

    private void playNextQueuedPlayerAction() {
        if (actionAnimationRunning || winnerAnimationRunning || applyingDeferredTableSetup) {
            return;
        }

        HandActionDTO action = pendingActionAnimations.poll();
        if (action == null) {
            applyDeferredVisualUpdates();
            return;
        }

        PlayerSeatController controller = seatControllerMap.get(action.playerId());
        if (controller == null) {
            playNextQueuedPlayerAction();
            return;
        }

        actionAnimationRunning = true;
        disableBettingControls();
        String actionText = formatActionText(action);
        String actionType = actionDisplayType(action);

        seatControllerMap.values().forEach(seat -> seat.setTurnActive(false));

        PlayerSeatController previousController = seatControllerMap.get(lastAnimatedActionPlayer);
        if (previousController != null && !action.playerId().equals(lastAnimatedActionPlayer)) {
            previousController.playActionHandoffOut();
        }

        controller.playActionHandoffIn(actionText, actionType);
        latestPlayerActions.put(action.playerId(), actionText);
        appendActionFeed(action.playerId() + " " + actionText.toLowerCase());

        if ("RAISE".equalsIgnoreCase(action.actionType()) || "CALL".equalsIgnoreCase(action.actionType())) {
            this.localPotSize += action.amount();
            playPotPulse();
        }

        PauseTransition focusPause = new PauseTransition(Duration.millis(650));
        focusPause.setOnFinished(event -> {
            controller.playActionAnimation(actionType, action.amount());
        });

        PauseTransition actionHold = new PauseTransition(Duration.millis(actionAnimationDuration(actionType)));
        PauseTransition betweenActionsDelay = new PauseTransition(Duration.millis(650));
        betweenActionsDelay.setOnFinished(event -> {
            lastAnimatedActionPlayer = action.playerId();
            actionAnimationRunning = false;
            playNextQueuedPlayerAction();
        });

        new SequentialTransition(focusPause, actionHold, betweenActionsDelay).play();
    }

    private int actionAnimationDuration(String actionType) {
        String normalizedAction = actionType == null ? "" : actionType.trim().toUpperCase();
        return switch (normalizedAction) {
            case "FOLD" -> 1900;
            case "RAISE", "ALL_IN" -> 2300;
            case "CALL", "CHECK" -> 1300;
            default -> 1200;
        };
    }

    private boolean isActionSequenceActive() {
        return actionAnimationRunning || !pendingActionAnimations.isEmpty();
    }

    private boolean isVisualSequenceActive() {
        return isActionSequenceActive() || winnerAnimationRunning;
    }

    private void startWinnerDisplayHold() {
        winnerAnimationRunning = true;
        disableBettingControls();

        if (winnerDisplayHold != null) {
            winnerDisplayHold.stop();
        }

        winnerDisplayHold = new PauseTransition(Duration.millis(WINNER_SCREEN_HOLD_MS));
        winnerDisplayHold.setOnFinished(event -> {
            winnerAnimationRunning = false;
            winnerDisplayHold = null;
            applyDeferredVisualUpdates();
            activeWinnerUsernames = List.of();
            activeWinnerPotSize = 0;
        });
        winnerDisplayHold.play();
    }

    private void applyDeferredVisualUpdates() {
        if (winnerAnimationRunning || actionAnimationRunning) {
            return;
        }

        if (hasDeferredGameState || deferredTableSnapshot != null) {
            applyingDeferredTableSetup = true;

            if (hasDeferredGameState) {
                GameState state = deferredGameState;
                deferredGameState = null;
                hasDeferredGameState = false;
                onGameStateChanged(state);
            }

            if (deferredTableSnapshot != null) {
                Map<String, Object> snapshot = deferredTableSnapshot;
                deferredTableSnapshot = null;
                handleTableSnapshot(snapshot);
            }

            Platform.runLater(() -> {
                applyingDeferredTableSetup = false;
                if (!pendingActionAnimations.isEmpty()) {
                    playNextQueuedPlayerAction();
                } else {
                    applyDeferredVisualUpdates();
                }
            });
            return;
        }

        if (!pendingActionAnimations.isEmpty()) {
            playNextQueuedPlayerAction();
            return;
        }

        if (hasDeferredHandResult) {
            List<String> winners = deferredWinnerUsernames;
            HandResult hand = deferredWinnerHand;
            int potSize = deferredWinnerPotSize;
            deferredWinnerUsernames = null;
            deferredWinnerHand = null;
            deferredWinnerPotSize = 0;
            hasDeferredHandResult = false;
            onHandResult(winners, hand, potSize);
            return;
        }

        if (deferredHandResultMessage != null) {
            GameMessageDTO handResultMessage = deferredHandResultMessage;
            deferredHandResultMessage = null;
            handleHandResultMessage(handResultMessage);
            return;
        }

        if (hasDeferredTurnPrompt) {
            String username = deferredTurnUsername;
            int amountToCall = deferredTurnAmountToCall;
            deferredTurnUsername = null;
            deferredTurnAmountToCall = 0;
            hasDeferredTurnPrompt = false;
            onPlayerTurn(username, amountToCall);
        }
    }

    // --- HELPER STRIP MAPPING METHODS ---

    private void sendAction(String actionType, int amount) {
        PokerWebSocketClient client = PokerWebSocketClient.getInstance();
        if (client != null && client.isOpen()) {
            client.sendPayload(new OutboundActionPayload(actionType, amount));
            if ("ADD_BOT".equals(actionType) && gameStatusLabel != null) {
                gameStatusLabel.setText("Adding bot...");
            }
        } else if (actionAPI != null) {
            if ("FOLD".equals(actionType)) actionAPI.Fold(localUsername);
            if ("CALL".equals(actionType)) actionAPI.Call(localUsername);
            if ("RAISE".equals(actionType)) actionAPI.Raise(localUsername, amount);
        } else if (gameStatusLabel != null) {
            gameStatusLabel.setText("Not connected to the game server.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : asList(value)) {
            result.add(asString(item));
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(asString(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void updateChipsDisplay(int playerChips, int potSize) {
        chipsInfoLabel.setText("Your Chips: $" + playerChips + "  |  Pot: $" + potSize);
    }

    private String formatActionText(HandActionDTO action) {
        String actionType = action.actionType() == null ? "" : action.actionType().trim().toUpperCase();
        if ("FOLD".equals(actionType)) {
            return "FOLDED - OUT";
        }
        if ("CALL".equalsIgnoreCase(action.actionType()) && action.amount() == 0) {
            return "CHECKS";
        }
        if ("CALL".equals(actionType)) {
            return action.amount() > 0 ? "CALLS $" + action.amount() : "CALLS";
        }
        if ("RAISE".equals(actionType)) {
            return action.amount() > 0 ? "RAISES $" + action.amount() : "RAISES";
        }
        if (action.amount() > 0) {
            return action.actionType() + " $" + action.amount();
        }
        return action.actionType();
    }

    private String actionDisplayType(HandActionDTO action) {
        if ("CALL".equalsIgnoreCase(action.actionType()) && action.amount() == 0) {
            return "CHECK";
        }
        return action.actionType();
    }

    private void appendActionFeed(String message) {
        if (actionFeedList == null || message == null || message.isBlank()) {
            return;
        }

        actionFeedList.getItems().add(message);
        while (actionFeedList.getItems().size() > 8) {
            actionFeedList.getItems().remove(0);
        }
        actionFeedList.scrollTo(actionFeedList.getItems().size() - 1);
        playActionFeedAnimation();
    }

    private int getLocalChipCount() {
        PlayerSeatController controller = seatControllerMap.get(localUsername);
        return controller == null ? 0 : controller.getCurrentChips();
    }

    private String tableLabel(int tableBuyIn, int smallBlind, int bigBlind) {
        if (tableBuyIn <= 0) {
            return "Table";
        }
        return "Table $" + tableBuyIn + " | Blinds $" + smallBlind + "/$" + bigBlind;
    }

    private void enableBettingControls() {
        if (isVisualSequenceActive()) {
            disableBettingControls();
            return;
        }
        if (foldButton != null) foldButton.setDisable(false);
        if (callButton != null) callButton.setDisable(false);
        if (raiseButton != null) raiseButton.setDisable(false);
    }

    private void disableBettingControls() {
        if (foldButton != null) foldButton.setDisable(true);
        if (callButton != null) callButton.setDisable(true);
        if (raiseButton != null) raiseButton.setDisable(true);
    }

    private String getImagePathForCard(Card card) {
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

    private List<Card> parseCardsString(String holeCardsStr) {
        List<Card> cards = new ArrayList<>();
        if (holeCardsStr == null || holeCardsStr.isEmpty() || "HIDDEN".equalsIgnoreCase(holeCardsStr)) {
            return cards;
        }

        String[] cardTokens = holeCardsStr.split(",");
        for (String token : cardTokens) {
            token = token.trim();
            if (token.length() == 2) {
                Card card = createCardFromChars(token.charAt(0), token.charAt(1));
                if (card != null) cards.add(card);
            }
        }
        return cards;
    }

    private Card createCardFromChars(char rankChar, char suitChar) {
        int value = switch (rankChar) {
            case '2' -> 2;   case '3' -> 3;   case '4' -> 4;
            case '5' -> 5;   case '6' -> 6;   case '7' -> 7;
            case '8' -> 8;   case '9' -> 9;
            case 'T' -> 10;  case 'J' -> 11;  case 'Q' -> 12;
            case 'K' -> 13;  case 'A' -> 14;
            default -> -1;
        };

        String suit = switch (suitChar) {
            case 'h' -> "Hearts";
            case 'd' -> "Diamonds";
            case 'c' -> "Clubs";
            case 's' -> "Spades";
            default -> null;
        };

        if (value == -1 || suit == null) return null;
        return new Card(value, suit);
    }
}
