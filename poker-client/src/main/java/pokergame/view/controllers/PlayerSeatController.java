package pokergame.view.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import pokergame.domain.model.Card;
import pokergame.domain.model.TableSeat;

public class PlayerSeatController {

    @FXML private Label usernameLabel;
    @FXML private Label chipsLabel;
    @FXML private Label actionLabel;
    @FXML private ImageView holeCard1;
    @FXML private ImageView holeCard2;

    private static final String CARD_BACK = "/images/back.png"; // Make sure you have a card back image!

    /**
     * Initial setup for the player's seat
     */
    public void setup(TableSeat seat) {
        usernameLabel.setText(seat.getUsername());
        updateChips(seat.getChipsOnTable());
        actionLabel.setText("");
        showCardBacks(); // Hide cards by default
    }

    public void updateChips(int amount) {
        chipsLabel.setText("$" + amount);
    }

    public void setAction(String action) {
        actionLabel.setText(action);
    }

    /**
     * Displays the actual card faces (Used for the local player or at Showdown)
     */
    public void revealCards(Card c1, Card c2) {
        holeCard1.setImage(new Image(getClass().getResource(getImagePath(c1)).toExternalForm()));
        holeCard2.setImage(new Image(getClass().getResource(getImagePath(c2)).toExternalForm()));
    }

    public void showCardBacks() {
        Image back = new Image(getClass().getResource(CARD_BACK).toExternalForm());
        holeCard1.setImage(back);
        holeCard2.setImage(back);
    }

    public void clearCards() {
        holeCard1.setImage(null);
        holeCard2.setImage(null);
    }

    private String getImagePath(Card card) {
        String suit = card.getSuit().toString().substring(0, 1).toUpperCase();
        int val = card.getValue();
        String valStr = switch (val) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(val);
        };
        return "/images/" + valStr + "-" + suit + ".png";
    }
}