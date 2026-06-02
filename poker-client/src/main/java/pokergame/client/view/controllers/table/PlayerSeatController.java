package pokergame.client.view.controllers.table;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokergame.GameContext;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;

import java.util.Objects;

public class PlayerSeatController {

    @FXML private VBox playerBox;
    @FXML private Label usernameLabel;
    @FXML private Label chipsLabel;
    @FXML private Label actionLabel;
    @FXML private HBox cardsBox;
    @FXML private ImageView holeCard1;
    @FXML private ImageView holeCard2;

    private String activeCardToken = null;

    private static final String CARD_BACK = "/images/BACK.png";
    private int currentChips;
    private String currentUsername;
    public void updateFromSnapshot(HandParticipantDTO seat, String clientUsername) {
        this.currentUsername = seat.playerUsername();
        usernameLabel.setText(currentUsername);

        // Synchronize chip stacks
        updateChips(seat.startChips());

        // FIX: Decouple card logic from action labels and route string tokens into the UI rendering engine
        String cardsToken = seat.holeCards();


        if (cardsToken == null || cardsToken.isBlank() || cardsToken.equals("[]")) {
            this.activeCardToken = null;
            clearCards();
        } else if (cardsToken.equalsIgnoreCase("HIDDEN")) {
            if (clientUsername.equals(GameContext.getPlayerProfile().username()) && this.activeCardToken != null) {
                return;
            }
            this.activeCardToken = null;
            showCardBacks();
        } else {
            this.activeCardToken = cardsToken;
            revealCardsFromToken(cardsToken);
        }
    }

    private void revealCardsFromToken(String token) {
        try {
            String[] cards = token.split(",");
            if (cards.length == 2) {
                String path1 = "/images/" + cards[0].trim() + ".png";
                String path2 = "/images/" + cards[1].trim() + ".png";

                Image img1 = new Image(getClass().getResource(path1).toExternalForm(), 70, 95, true, true);
                Image img2 = new Image(getClass().getResource(path2).toExternalForm(), 70, 95, true, true);

                holeCard1.setImage(img1);
                holeCard2.setImage(img2);
                javafx.application.Platform.runLater(() -> {
                    holeCard1.setVisible(true);
                    holeCard2.setVisible(true);
                    cardsBox.setOpacity(1);
                    cardsBox.setTranslateY(0);
                    cardsBox.setRotate(0);

                    // Debug log to confirm the image actually loaded pixels
                    System.out.println("[UI Debug] Rendered cards: " + token + " | Img1 Width: " + img1.getWidth());
                });

            }
        } catch (Exception e) {
            System.err.println("[UI Card Error] Could not load face card assets for: " + token);
            showCardBacks(); // Fallback UI
        }
    }

    public void showCardBacks() {
        try {
            Image backImage = new Image(getClass().getResource(CARD_BACK).toExternalForm(), 70, 95, true, true);

            javafx.application.Platform.runLater(() -> {
                holeCard1.setImage(backImage);
                holeCard2.setImage(backImage);
                holeCard1.setVisible(true);
                holeCard2.setVisible(true);
                cardsBox.setOpacity(1.0);
                cardsBox.setTranslateY(0);
                cardsBox.setVisible(true);
            });
        } catch (Exception e) {
            System.err.println("[UI Error] Failed to render card back textures: " + e.getMessage());
        }
    }

    /**
     * Converts shorthand representation structures like "A-S" to filesystem paths like "/images/14-S.png"
     */
    private String parseTokenToPath(String singleCardToken) {
        // Splits "A-S" into components parts[0]="A", parts[1]="S"
        String[] parts = singleCardToken.split("-");
        if (parts.length != 2) return CARD_BACK;

        String rank = parts[0].trim().toUpperCase();
        String suit = parts[1].trim().toUpperCase();

        // Map alphabetic high-card faces back to numeric value formats
        switch (rank) {
            case "A"  -> rank = "14";
            case "K"  -> rank = "13";
            case "Q"  -> rank = "12";
            case "J"  -> rank = "11";
        }

        return "/images/" + rank + "-" + suit + ".png";
    }

    public void updateChips(int amount) {
        int previousChips = currentChips;
        this.currentChips = amount;
        chipsLabel.setText("$" + amount);
    }

    public void setAction(String action) {
        actionLabel.setText(action == null ? "" : action);
    }

    public void restoreActionVisual(String actionToken) {
        String normalizedAction = actionToken == null ? "" : actionToken.toUpperCase();
        setAction(normalizedAction);

        if (normalizedAction.contains("FOLD")) {
            applyActionStyle("FOLD");
            setStyleClass(playerBox, "player-folded", true);
            cardsBox.setOpacity(0.15);
            cardsBox.setTranslateY(34);
            cardsBox.setRotate(-10);
            playerBox.setOpacity(0.38);
        } else if (normalizedAction.contains("RAISE")) {
            applyActionStyle("RAISE");
        } else if (normalizedAction.contains("CALL")) {
            applyActionStyle("CALL");
        } else if (normalizedAction.contains("CHECK")) {
            applyActionStyle("CHECK");
        }
    }

    public void revealCards(Card c1, Card c2) {
        holeCard1.setImage(new Image(getClass().getResource(getImagePath(c1)).toExternalForm()));
        holeCard2.setImage(new Image(getClass().getResource(getImagePath(c2)).toExternalForm()));
    }

    public void clearCards() {
        holeCard1.setImage(null);
        holeCard2.setImage(null);
    }

    public void resetSeatVisuals() {
        playerBox.setOpacity(1);
        playerBox.setScaleX(1);
        playerBox.setScaleY(1);
        playerBox.setTranslateY(0);
        setStyleClass(playerBox, "player-folded", false);
        setStyleClass(playerBox, "player-raised", false);
        setStyleClass(playerBox, "player-action-focus", false);
        setStyleClass(playerBox, "player-winner", false);
        resetCardsVisuals();
    }

    private void resetCardsVisuals() {
        cardsBox.setOpacity(1);
        cardsBox.setTranslateY(0);
        cardsBox.setRotate(0);
    }

    public void applyActionStyle(String actionType) {
        actionLabel.getStyleClass().removeAll(
                "action-badge", "action-fold", "action-call",
                "action-check", "action-raise", "action-pending", "action-win"
        );
        actionLabel.getStyleClass().add("action-badge");

        switch (actionType.toUpperCase()) {
            case "FOLD" -> actionLabel.getStyleClass().add("action-fold");
            case "CALL" -> actionLabel.getStyleClass().add("action-call");
            case "CHECK" -> actionLabel.getStyleClass().add("action-check");
            case "RAISE", "ALL_IN" -> actionLabel.getStyleClass().add("action-raise");
            case "PENDING" -> actionLabel.getStyleClass().add("action-pending");
            case "WIN" -> actionLabel.getStyleClass().add("action-win");
        }
    }

    public void setStyleClass(Node node, String styleClass, boolean enabled) {
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private void renderFaceUpCards(String cardTokenString) {
        try {
            String[] tokens = cardTokenString.split("-");
            if (tokens.length == 2) {
                Card c1 = parseTokenToCard(tokens[0]);
                Card c2 = parseTokenToCard(tokens[1]);
                revealCards(c1, c2);
            }
        } catch (Exception e) {
            System.err.println("Failed to render hole cards: " + cardTokenString);
            showCardBacks();
        }
    }

    private Card parseTokenToCard(String token) {
        // Split the token into Value and Suit using the hyphen
        String[] parts = token.split("-");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid card token format: " + token);
        }

        String valStr = parts[0].toUpperCase().trim(); // "A", "10", "K"
        String suitStrRaw = parts[1].toUpperCase().trim(); // "S", "H", "C", "D"

        // 1. Map the face cards to integer values
        int value = switch (valStr) {
            case "J" -> 11;
            case "Q" -> 12;
            case "K" -> 13;
            case "A" -> 14;
            default -> Integer.parseInt(valStr);
        };

        // 2. Map the single-letter suit back to the full word required by your Card model
        String suitStr = switch (suitStrRaw) {
            case "S" -> "Spades";
            case "H" -> "Hearts";
            case "D" -> "Diamonds";
            case "C" -> "Clubs";
            default -> "Spades";
        };

        return new Card(value, suitStr);
    }

    private String getImagePath(Card card) {
        // Aligns precisely with your engine's internal 14-S.png model conventions
        return "/images/" + card.toImageString();
    }

    // Pass-through Getters for our Dedicated Animation Engine
    public VBox getPlayerBox() { return playerBox; }
    public HBox getCardsBox() { return cardsBox; }
    public Label getActionLabel() { return actionLabel; }
    public Label getChipsLabel() { return chipsLabel; }
    public ImageView getHoleCard1() { return holeCard1; }
    public ImageView getHoleCard2() { return holeCard2; }
    public int getCurrentChips() { return currentChips; }
    public String getCurrentUsername() { return currentUsername; }
}