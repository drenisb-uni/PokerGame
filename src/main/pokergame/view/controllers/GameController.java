package pokergame.view.controllers;

import java.util.HashMap;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import pokergame.GameContext;
import pokergame.domain.model.Card;
import pokergame.domain.model.TableSeat;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.PokerGameEngine;

import java.util.List;

public class GameController implements IGameEventListener {

    private String currentActivePlayerUsername;

    private Map<String, PlayerSeatController> seatControllerMap = new HashMap<>();

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
    private int currentCommunityCardIndex = 0;
    private int currentAmountToCall = 0;

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

//    @Override
//    public void onPlayerTurn(TableSeat activePlayer, int amountToCall) {
//        Platform.runLater(() -> {
//            if (activePlayer.getUsername().equals(getLocalUsername())) {
//                this.currentAmountToCall = amountToCall;
//                enableBettingControls();
//
//                callButton.setText(amountToCall == 0 ? "Check" : "Call $" + amountToCall);
//                updateChipsDisplay(activePlayer.getChipsOnTable(), engine.getPotSize()); // Assuming you add getPotSize()
//            } else {
//                disableBettingControls();
//                chipsInfoLabel.setText("Waiting for " + activePlayer.getUsername() + " to act...");
//            }
//        });
//    }

    @Override
    public void onPlayerTurn(TableSeat activePlayer, int amountToCall) {
        Platform.runLater(() -> {
            // 1. Save whoever the engine is waiting for
            this.currentActivePlayerUsername = activePlayer.getUsername();
            this.currentAmountToCall = amountToCall;

            // 2. ALWAYS enable the buttons so you can test!
            enableBettingControls();

            // 3. Update the UI so you know who you are acting as
            callButton.setText(amountToCall == 0 ? "Check" : "Call $" + amountToCall);
            gameStatusLabel.setText("WAITING ON: " + currentActivePlayerUsername);

            // Bonus: Highlight the active player visually!
            chipsInfoLabel.setText("You are currently controlling: " + currentActivePlayerUsername);
        });
    }

    @Override
    public void onPlayerAction(TableSeat player, String actionType, int amount) {
        Platform.runLater(() -> {
            PlayerSeatController controller = seatControllerMap.get(player.getUsername());
            if (controller != null) {
                controller.setAction(actionType + (amount > 0 ? " $" + amount : ""));
                controller.updateChips(player.getChipsOnTable());
            }
        });
    }

    @Override
    public void onHandResult(List<TableSeat> winners, HandResult winningHand, int potSize) {
        Platform.runLater(() -> {
            StringBuilder winMsg = new StringBuilder();
            for (TableSeat winner : winners) {
                winMsg.append(winner.getUsername()).append(" ");
            }
            winMsg.append("won $").append(potSize);
            chipsInfoLabel.setText(winMsg.toString());
        });
    }

    @Override
    public void onNewSeatOccupied(TableSeat tableSeat) {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PlayerSeat.fxml"));
                VBox seatUI = loader.load();

                // Get the specific controller for this seat
                PlayerSeatController controller = loader.getController();
                controller.setup(tableSeat);

                // If this is the local player, show their cards immediately
                if (tableSeat.getUsername().equals(getLocalUsername())) {
                    // Assuming TableSeat has a getCards() method
                    List<Card> cards = tableSeat.getHoleCards();
                    if (cards.size() == 2) {
                        controller.revealCards(cards.get(0), cards.get(1));
                    }
                }

                playersContainer.getChildren().add(seatUI);
                seatControllerMap.put(tableSeat.getUsername(), controller);

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
}