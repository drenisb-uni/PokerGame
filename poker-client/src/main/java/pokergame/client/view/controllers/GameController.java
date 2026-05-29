package pokergame.client.view.controllers;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pokergame.client.utils.EventBus;
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
    @FXML private Button foldButton;
    @FXML private Button callButton;
    @FXML private Button raiseButton;

    // Raise Popup Elements
    @FXML private StackPane raisePopupOverlay;
    @FXML private TextField raiseAmountInput;

    // --- DECOUPLED NETWORK STATE ---
    private IPublicActionAPI actionAPI; // Bound directly to your WebSocketClientAPI
    private String localUsername;       // Cached locally from MainApp injection context

    // --- GAME STATE VARIABLES ---
    private final Map<String, PlayerSeatController> seatControllerMap = new HashMap<>();
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
        if (actionAPI == null) return;
        disableBettingControls();
        actionAPI.Fold(localUsername);
    }

    @FXML
    public void handleCall(ActionEvent event) {
        if (actionAPI == null) return;
        disableBettingControls();
        actionAPI.Call(localUsername);
    }

    @FXML
    public void handleConfirmRaise(ActionEvent event) {
        if (actionAPI == null) return;
        try {
            int amount = Integer.parseInt(raiseAmountInput.getText().trim());

            if (amount <= currentAmountToCall) {
                System.out.println("Raise must be greater than the current call amount!");
                return;
            }

            disableBettingControls();
            raisePopupOverlay.setVisible(false);

            actionAPI.Raise(localUsername, amount);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    @FXML
    public void handleAllIn(ActionEvent event) {
        if (actionAPI == null) return;
        disableBettingControls();
        raisePopupOverlay.setVisible(false);

        // Fetch current active chip reserves straight from the UI tracker
        PlayerSeatController localSeat = seatControllerMap.get(localUsername);
        int playerTotalChips = (localSeat != null) ? localSeat.getCurrentChips() : 0;

        actionAPI.Raise(localUsername, playerTotalChips);
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
                case PRE_FLOP_BETTING:
                    currentCommunityCardIndex = 0;
                    for (ImageView iv : communityCards) {
                        iv.setImage(null);
                    }
                    break;
                case HAND_OVER:
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
                controller.setAction(action.actionType() + (action.amount() > 0 ? " $" + action.amount() : ""));

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

    // --- HELPER STRIP MAPPING METHODS ---

    private void updateChipsDisplay(int playerChips, int potSize) {
        chipsInfoLabel.setText("Your Chips: $" + playerChips + "  |  Pot: $" + potSize);
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