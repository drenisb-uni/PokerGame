package pokergame.client.view.controllers;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private int currentCommunityCardIndex = 0;
    private int currentAmountToCall = 0;
    private int localPotSize = 0;

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
        disableBettingControls();
        sendAction("FOLD", 0);
    }

    @FXML
    public void handleCall(ActionEvent event) {
        disableBettingControls();
        sendAction("CALL", 0);
    }

    @FXML
    public void handleConfirmRaise(ActionEvent event) {
        try {
            int amount = Integer.parseInt(raiseAmountInput.getText().trim());

            if (amount <= currentAmountToCall) {
                System.out.println("Raise must be greater than the current call amount!");
                return;
            }

            disableBettingControls();
            raisePopupOverlay.setVisible(false);

            sendAction("RAISE", amount);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    @FXML
    public void handleAllIn(ActionEvent event) {
        disableBettingControls();
        raisePopupOverlay.setVisible(false);

        // Fetch current active chip reserves straight from the UI tracker
        PlayerSeatController localSeat = seatControllerMap.get(localUsername);
        int playerTotalChips = (localSeat != null) ? localSeat.getCurrentChips() : 0;

        sendAction("RAISE", playerTotalChips);
    }

    @FXML
    public void handleAddBot(ActionEvent event) {
        sendAction("ADD_BOT", 0);
    }

    @FXML
    public void handleLeaveTable(ActionEvent event) {
        sendAction("LEAVE_TABLE", 0);
        PokerWebSocketClient client = PokerWebSocketClient.getInstance();
        if (client != null && client.isOpen()) {
            client.close();
        }
        SceneManager.switchScene("Lobby.fxml");
    }

    @FXML
    public void showRaisePopup(ActionEvent event) {
        raiseAmountInput.clear();
        raisePopupOverlay.setVisible(true);
    }

    @FXML
    public void hideRaisePopup(ActionEvent event) {
        raisePopupOverlay.setVisible(false);
    }

    // --- ENGINE EVENT LISTENERS (Inbound Server Events) ---

    @Override
    public void onGameStateChanged(GameState newState) {
        Platform.runLater(() -> {
            switch (newState) {
                case WAITING_FOR_PLAYERS:
                    if (gameStatusLabel != null) gameStatusLabel.setText("Waiting for players...");
                    break;
                case PRE_FLOP_BETTING:
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
                    if (gameStatusLabel != null) gameStatusLabel.setText("Showing cards...");
                    disableBettingControls();
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

            if (username.equals(localUsername)) {
                this.currentAmountToCall = amountToCall;
                enableBettingControls();
                callButton.setText(amountToCall == 0 ? "Check" : "Call $" + amountToCall);
            } else {
                disableBettingControls();
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
            PlayerSeatController controller = seatControllerMap.get(action.playerId());
            if (controller != null) {
                String actionText = formatActionText(action);
                controller.setAction(actionText);
                latestPlayerActions.put(action.playerId(), actionText);
                appendActionFeed(action.playerId() + " " + actionText.toLowerCase());

                if ("RAISE".equalsIgnoreCase(action.actionType()) || "CALL".equalsIgnoreCase(action.actionType())) {
                    this.localPotSize += action.amount();
                }
            }
        });
    }

    @Override
    public void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize) {
        Platform.runLater(() -> {
            StringBuilder winMsg = new StringBuilder();
            for (String username : winnerUsernames) {
                winMsg.append(username).append(" ");
            }

            String handTypeStr = (winnerHand != null && winnerHand.getType() != null)
                    ? winnerHand.getType().toString().replace("_", " ")
                    : "Muck";

            winMsg.append("won $").append(potSize).append(" with ").append(handTypeStr);
            chipsInfoLabel.setText(winMsg.toString());
            this.localPotSize = 0;
        });
    }

    @Override
    public void onTableSnapshotBroadcast(Map<String, Object> snapshotPayload) {

    }

    @Override
    public void onTargetedTableSnapshot(String playerId, Map<String, Object> snapshotPayload) {

    }

    @Override
    public void onNewSeatOccupied(HandParticipantDTO participant) {
        Platform.runLater(() -> {
            if (seatControllerMap.containsKey(participant.playerUsername())) {
                // If seat already populated locally, refresh current counts instead of inflating layouts
                PlayerSeatController existingCtrl = seatControllerMap.get(participant.playerUsername());
                existingCtrl.setup(participant);
                return;
            }

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                VBox seatUI = loader.load();

                PlayerSeatController controller = loader.getController();
                controller.setup(participant);

                if (participant.playerUsername().equals(localUsername) && !"HIDDEN".equals(participant.holeCards())) {
                    List<Card> cards = parseCardsString(participant.holeCards());
                    if (cards.size() == 2) {
                        controller.revealCards(cards.get(0), cards.get(1));
                    }
                }

                playersContainer.getChildren().add(seatUI);
                seatControllerMap.put(participant.playerUsername(), controller);

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
        String foldingPlayer = event.sender();
        System.out.println("[Table UI] Grey out avatar for: " + foldingPlayer);
        // TODO: Update UI
    }

    private void handleChat(GameMessageDTO event) {
        // TODO: Append event.payload() to the chat box UI
    }

    private void handleTableSnapshot(GameMessageDTO event) {
        Platform.runLater(() -> {
            Map<String, Object> payload = asMap(event.payload());
            List<?> seats = asList(payload.get("seats"));
            int maxSeats = asInt(payload.get("maxSeats"), 6);
            int potSize = asInt(payload.get("potSize"), this.localPotSize);
            String gameState = asString(payload.get("gameState"));
            int tableBuyIn = asInt(payload.get("tableBuyIn"), 0);
            int smallBlind = asInt(payload.get("smallBlind"), 0);
            int bigBlind = asInt(payload.get("bigBlind"), 0);

            playersContainer.getChildren().clear();
            seatControllerMap.clear();
            this.localPotSize = potSize;

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
                    gameStatusLabel.setText("Showing cards...");
                } else if (!gameState.isBlank()) {
                    gameStatusLabel.setText(tableLabel(tableBuyIn, smallBlind, bigBlind));
                }
            }

            if (!"HAND_OVER".equals(gameState)) {
                chipsInfoLabel.setText("Your Chips: $" + getLocalChipCount() + "  |  Pot: $" + this.localPotSize);
            }
        });
    }

    private void handleGameStateMessage(GameMessageDTO event) {
        try {
            GameState state = GameState.valueOf(asString(event.payload()));
            if (state == GameState.PRE_FLOP_BETTING) {
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
            Map<String, Object> payload = asMap(event.payload());
            String winners = String.join(" ", asStringList(payload.get("winnerUsernames")));
            String hand = asString(payload.get("winnerHand"));
            int potSize = asInt(payload.get("potSize"), 0);
            chipsInfoLabel.setText(winners + " won $" + potSize + " with " + (hand.isBlank() ? "Muck" : hand.replace("_", " ")));
            appendActionFeed(winners + " won $" + potSize);
            localPotSize = 0;
        });
    }

    private void renderSeat(HandParticipantDTO participant) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
            VBox seatUI = loader.load();

            PlayerSeatController controller = loader.getController();
            controller.setup(participant);
            if (latestPlayerActions.containsKey(participant.playerUsername())) {
                controller.setAction(latestPlayerActions.get(participant.playerUsername()));
            }

            List<Card> visibleCards = parseCardsString(participant.holeCards());
            if (visibleCards.size() == 2) {
                controller.revealCards(visibleCards.get(0), visibleCards.get(1));
            }

            playersContainer.getChildren().add(seatUI);
            seatControllerMap.put(participant.playerUsername(), controller);
        } catch (IOException e) {
            e.printStackTrace();
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
        if (action.amount() > 0) {
            return action.actionType() + " $" + action.amount();
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
