package pokergame.view.controllers;

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
import pokergame.GameContext;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.model.TableSeat;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.PokerGameEngine;

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

    // Raise Popup Elements
    @FXML private StackPane raisePopupOverlay;
    @FXML private TextField raiseAmountInput;

    // --- GAME STATE VARIABLES ---
    private PokerGameEngine engine;
    private Map<String, PlayerSeatController> seatControllerMap = new HashMap<>();
    private String currentActivePlayerUsername;
    private int currentCommunityCardIndex = 0;
    private int currentAmountToCall = 0;
    private int localPotSize = 0;


    @FXML
    public void initialize() {
        communityCards = new ImageView[]{ commCard1, commCard2, commCard3, commCard4, commCard5 };

        // Hide the raise popup initially
        raisePopupOverlay.setVisible(false);

        engine = GameContext.getPokerGameEngine();
        engine.addObserver(this);

        //disableBettingControls();

        // 2. NOW tell the engine to start the game!
        engine.fillTableSeats(1000);
        engine.startNewHand();
    }

    private String getLocalUsername() {
        return GameContext.getPlayerProfile().username();
    }

    // --- BUTTON HANDLERS ---

    // TESTABLE GAME LOOP ----- TEMPORARY METHODS
    @FXML
    public void handleFold(ActionEvent event) {
        disableBettingControls();
        // Submit the fold for whoever's turn it currently is
        engine.executePlayerAction(currentActivePlayerUsername, "FOLD", 0);
    }

    @FXML
    public void handleCall(ActionEvent event) {
        disableBettingControls();
        // Submit the call for whoever's turn it currently is
        engine.executePlayerAction(currentActivePlayerUsername, "CALL", currentAmountToCall);
    }

    @FXML
    public void handleConfirmRaise(ActionEvent event) {
        try {
            int amount = Integer.parseInt(raiseAmountInput.getText());
            if (amount <= currentAmountToCall) {
                System.out.println("Raise must be greater than the current call amount!");
                return;
            }

            disableBettingControls();
            raisePopupOverlay.setVisible(false);

            // Submit the raise for whoever's turn it currently is
            engine.executePlayerAction(currentActivePlayerUsername, "RAISE", amount);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    //-----------------------------------
    /* METHODS FOR MULTIPLAYER AND/OR AI PLAYERS

    @FXML
    public void handleFold(ActionEvent event) {
        disableBettingControls();
        engine.executePlayerAction(getLocalUsername(), "FOLD", 0);
    }

    @FXML
    public void handleCall(ActionEvent event) {
        disableBettingControls();
        engine.executePlayerAction(getLocalUsername(), "CALL", currentAmountToCall);
    }


    @FXML
    public void handleConfirmRaise(ActionEvent event) {
        try {
            int amount = Integer.parseInt(raiseAmountInput.getText());

            // Basic validation (you can expand this to check against player's balance)
            if (amount <= currentAmountToCall) {
                System.out.println("Raise must be greater than the current call amount!");
                return;
            }

            disableBettingControls();
            raisePopupOverlay.setVisible(false);
            engine.executePlayerAction(getLocalUsername(), "RAISE", amount);

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number for the raise amount.");
        }
    }
*/

    @FXML
    public void showRaisePopup(ActionEvent event) {
        raiseAmountInput.clear();
        raisePopupOverlay.setVisible(true);
    }

    @FXML
    public void hideRaisePopup(ActionEvent event) {
        raisePopupOverlay.setVisible(false);
    }

    @FXML
    public void handleAllIn(ActionEvent event) {
        disableBettingControls();
        raisePopupOverlay.setVisible(false);

        // TODO: Get the actual player's total chips from your domain model
        int playerTotalChips = 500;

        engine.executePlayerAction(getLocalUsername(), "RAISE", playerTotalChips);
    }


    // --- ENGINE EVENT LISTENERS ---

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
                        System.out.println("Could not load image: " + imagePath);
                    }
                    currentCommunityCardIndex++;
                }
            }
        });
    }

    @Override
    public void onPlayerTurn(String username, int amountToCall) {
        Platform.runLater(() -> {
            if (username.equals(getLocalUsername())) {
                this.currentAmountToCall = amountToCall;
                enableBettingControls();

                callButton.setText(amountToCall == 0 ? "Check" : "Call $" + amountToCall);

                // Look up the local seat controller to get the player's chip view
                PlayerSeatController controller = seatControllerMap.get(username);
                int currentChips = (controller != null) ? controller.getCurrentChips() : 0;
                updateChipsDisplay(currentChips, this.localPotSize);
            } else {
                disableBettingControls();
                chipsInfoLabel.setText("Waiting for " + username + " to act...");
            }
        });
    }

    @Override
    public void onPlayerAction(HandActionDTO action) {
        Platform.runLater(() -> {
            PlayerSeatController controller = seatControllerMap.get(action.playerId());
            if (controller != null) {
                // Update the text action tag (e.g., "Raise $50")
                controller.setAction(action.actionType() + (action.amount() > 0 ? " $" + action.amount() : ""));

                // Track total pot locally based on player investments
                if (action.amount() > 0) {
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

            winMsg.append("won $").append(potSize)
                    .append(" with a ").append(winnerHand.getType().toString().replace("_", " "));

            chipsInfoLabel.setText(winMsg.toString());
            this.localPotSize = 0; // Reset local pot tracking for the next hand
        });
    }

    @Override
    public void onNewSeatOccupied(HandParticipantDTO participant) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                VBox seatUI = loader.load();

                PlayerSeatController controller = loader.getController();

                // CRITICAL: Update PlayerSeatController.setup() to take HandParticipantDTO instead of TableSeat
                controller.setup(participant);

                // Reveal cards ONLY if they belong to the local player and are not masked as "HIDDEN"
                if (participant.playerUsername().equals(getLocalUsername()) && !"HIDDEN".equals(participant.holeCards())) {
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

    // --- HELPER METHODS ---

    private void updateChipsDisplay(int playerChips, int potSize) {
        chipsInfoLabel.setText("Your Chips: $" + playerChips + "  |  Pot: $" + potSize);
    }

    private void enableBettingControls() {
        if(foldButton != null) foldButton.setDisable(false);
        if(callButton != null) callButton.setDisable(false);
        if(raiseButton != null) raiseButton.setDisable(false);
    }

    private void disableBettingControls() {
        if(foldButton != null) foldButton.setDisable(true);
        if(callButton != null) callButton.setDisable(true);
        if(raiseButton != null) raiseButton.setDisable(true);
    }

    private String getImagePathForCard(Card card) {
        String suitStr = card.getSuit().toString().substring(0, 1).toUpperCase();

        String valStr;
        int val = card.getValue();
        if (val == 11) valStr = "J";
        else if (val == 12) valStr = "Q";
        else if (val == 13) valStr = "K";
        else if (val == 14) valStr = "A";
        else valStr = String.valueOf(val);

        return "/images/" + valStr + "-" + suitStr + ".png";
    }

    // TODO move to dedicated utility class
    private List<Card> parseCardsString(String holeCardsStr) {
        List<Card> cards = new ArrayList<>();

        // 1. Guard clause: Handle hidden cards or empty data safely
        if (holeCardsStr == null || holeCardsStr.isEmpty() || "HIDDEN".equalsIgnoreCase(holeCardsStr)) {
            return cards;
        }

        // 2. Split the tokenized string by the comma
        String[] cardTokens = holeCardsStr.split(",");
        for (String token : cardTokens) {
            token = token.trim();
            if (token.length() == 2) {
                Card card = createCardFromChars(token.charAt(0), token.charAt(1));
                if (card != null) {
                    cards.add(card);
                }
            }
        }
        return cards;
    }

    private Card createCardFromChars(char rankChar, char suitChar) {
        // 1. Map the single rank character to your model's integer value
        int value = switch (rankChar) {
            case '2' -> 2;   case '3' -> 3;   case '4' -> 4;
            case '5' -> 5;   case '6' -> 6;   case '7' -> 7;
            case '8' -> 8;   case '9' -> 9;
            case 'T' -> 10;  // Ten
            case 'J' -> 11;  // Jack
            case 'Q' -> 12;  // Queen
            case 'K' -> 13;  // King
            case 'A' -> 14;  // Ace
            default -> -1;
        };

        // 2. Map the single suit character to your model's exact String naming
        String suit = switch (suitChar) {
            case 'h' -> "Hearts";
            case 'd' -> "Diamonds";
            case 'c' -> "Clubs";
            case 's' -> "Spades";
            default -> null;
        };

        // 3. If either check fails, reject the token safely
        if (value == -1 || suit == null) {
            System.err.println("Invalid card characters received: " + rankChar + suitChar);
            return null;
        }

        // 4. Instantiate utilizing your exact constructor logic (which auto-assigns color)
        return new Card(value, suit);
    }
}