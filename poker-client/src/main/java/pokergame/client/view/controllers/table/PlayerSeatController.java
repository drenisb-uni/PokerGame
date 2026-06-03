package pokergame.client.view.controllers.table;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import pokergame.domain.dto.HandParticipantDTO;

import java.net.URL;

public class PlayerSeatController {

    // =================================================================================
    // FXML UI ELEMENTS (Strictly preserving your existing bindings)
    // =================================================================================
    @FXML private VBox playerBox;
    @FXML private Label usernameLabel;
    @FXML private Label chipsLabel;
    @FXML private Label actionLabel;
    @FXML private HBox cardsBox;
    @FXML private ImageView holeCard1;
    @FXML private ImageView holeCard2;

    private static final String CARD_BACK_PATH = "/images/BACK.png";

    private String currentUsername;
    private int currentChips;

    // =================================================================================
    // INITIALIZATION (Called only once per session/reconnect by GameController)
    // =================================================================================

    public void updateFromSnapshot(HandParticipantDTO seat, String clientUsername) {
        this.currentUsername = seat.playerUsername();

        Platform.runLater(() -> {
            usernameLabel.setText(currentUsername);
            resetSeatVisuals();
        });

        updateChips(seat.startChips());
        setHoleCards(seat.holeCards());

        if (seat.hasFolded()) {
            setPlayerAction("FOLD");
        } else {
            setPlayerAction("");
        }
    }

    // =================================================================================
    // DELTA-BASED UI UPDATE METHODS (Surgical endpoints for NetworkHandler/Animations)
    // =================================================================================

    /**
     * SURGICAL DELTA: Updates chips instantly.
     */
    public void updateChips(int amount) {
        this.currentChips = amount;
        Platform.runLater(() -> chipsLabel.setText("$" + amount));
    }

    /**
     * SURGICAL DELTA: Processes a card token string to render exact face cards or backs.
     * Expected inputs: "HIDDEN", "[14-S, 13-H]", "[As, Kh]", or null/empty.
     */
    public void setHoleCards(String cardsToken) {
        if (cardsToken == null || cardsToken.isBlank() || cardsToken.equals("[]")) {
            clearCards();
        } else if (cardsToken.equalsIgnoreCase("HIDDEN")) {
            showCardBacks();
        } else {
            revealFaceCards(cardsToken);
        }
    }

    /**
     * SURGICAL DELTA: Updates the action badge (Call, Raise, Fold) and applies UI styling.
     */
    public void setPlayerAction(String actionToken) {
        String normalizedAction = actionToken == null ? "" : actionToken.toUpperCase().trim();

        Platform.runLater(() -> {
            actionLabel.setText(normalizedAction);

            // Clear previous CSS action states
            actionLabel.getStyleClass().removeAll(
                    "action-badge", "action-fold", "action-call",
                    "action-check", "action-raise", "action-pending", "action-win"
            );

            if (normalizedAction.isEmpty()) return;

            actionLabel.getStyleClass().add("action-badge");

            // Re-normalize layout before applying new action modifiers
            resetSeatVisuals();

            if (normalizedAction.contains("FOLD")) {
                actionLabel.getStyleClass().add("action-fold");
                applyFoldVisuals();
            } else if (normalizedAction.contains("RAISE") || normalizedAction.contains("ALL_IN") || normalizedAction.contains("ALL IN")) {
                actionLabel.getStyleClass().add("action-raise");
            } else if (normalizedAction.contains("CALL")) {
                actionLabel.getStyleClass().add("action-call");
            } else if (normalizedAction.contains("CHECK")) {
                actionLabel.getStyleClass().add("action-check");
            } else if (normalizedAction.contains("WIN")) {
                actionLabel.getStyleClass().add("action-win");
                setStyleClass(playerBox, "player-winner", true);
            }
        });
    }

    // =================================================================================
    // CARD RENDERING LOGIC (Deduplicated and Unified)
    // =================================================================================

    private void revealFaceCards(String token) {
        try {
            // Strip brackets if present (e.g., "[14-S, 13-H]" -> "14-S, 13-H")
            String cleanToken = token.replace("[", "").replace("]", "");
            String[] cards = cleanToken.split(",");

            if (cards.length == 2) {
                String path1 = resolveImagePath(cards[0].trim());
                String path2 = resolveImagePath(cards[1].trim());

                Image img1 = loadImageSafely(path1);
                Image img2 = loadImageSafely(path2);

                Platform.runLater(() -> {
                    holeCard1.setImage(img1);
                    holeCard2.setImage(img2);
                    holeCard1.setVisible(true);
                    holeCard2.setVisible(true);

                    resetCardsVisuals();
                });
            }
        } catch (Exception e) {
            System.err.println("[UI Card Error] Failed to parse/load face cards for token: " + token);
            showCardBacks(); // Safe fallback
        }
    }

    private void showCardBacks() {
        Image backImage = loadImageSafely(CARD_BACK_PATH);
        Platform.runLater(() -> {
            holeCard1.setImage(backImage);
            holeCard2.setImage(backImage);
            holeCard1.setVisible(true);
            holeCard2.setVisible(true);

            resetCardsVisuals();
        });
    }

    private void clearCards() {
        Platform.runLater(() -> {
            holeCard1.setImage(null);
            holeCard2.setImage(null);
            holeCard1.setVisible(false);
            holeCard2.setVisible(false);
        });
    }

    /**
     * Maps variations of card tokens (like "A-S" or "14-S") to the correct filesystem asset.
     */
    private String resolveImagePath(String cardToken) {
        String[] parts = cardToken.split("-");

        // If it's correctly formatted as "Rank-Suit"
        if (parts.length == 2) {
            String rank = parts[0].toUpperCase();
            String suit = parts[1].toUpperCase();

            // Convert face cards to numeric files if necessary
            rank = switch (rank) {
                case "A" -> "14";
                case "K" -> "13";
                case "Q" -> "12";
                case "J" -> "11";
                default -> rank;
            };
            return "/images/" + rank + "-" + suit + ".png";
        }

        // Fallback assuming the string is already a valid image prefix (e.g., "14-S")
        return "/images/" + cardToken + ".png";
    }

    private Image loadImageSafely(String path) {
        URL resource = getClass().getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Asset not found at path: " + path);
        }
        return new Image(resource.toExternalForm(), 70, 95, true, true);
    }

    // =================================================================================
    // VISUAL STYLING HELPERS
    // =================================================================================

    private void resetSeatVisuals() {
        playerBox.setOpacity(1.0);
        playerBox.setScaleX(1.0);
        playerBox.setScaleY(1.0);
        playerBox.setTranslateY(0);

        setStyleClass(playerBox, "player-folded", false);
        setStyleClass(playerBox, "player-raised", false);
        setStyleClass(playerBox, "player-action-focus", false);
        setStyleClass(playerBox, "player-winner", false);

        resetCardsVisuals();
    }

    private void resetCardsVisuals() {
        cardsBox.setOpacity(1.0);
        cardsBox.setTranslateY(0);
        cardsBox.setRotate(0);
        cardsBox.setVisible(true);
    }

    private void applyFoldVisuals() {
        setStyleClass(playerBox, "player-folded", true);
        cardsBox.setOpacity(0.15);
        cardsBox.setTranslateY(34);
        cardsBox.setRotate(-10);
        playerBox.setOpacity(0.38);
    }

    private void setStyleClass(Node node, String styleClass, boolean enabled) {
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    // =================================================================================
    // PASS-THROUGH GETTERS FOR ANIMATION ENGINE
    // =================================================================================

    public VBox getPlayerBox() { return playerBox; }
    public HBox getCardsBox() { return cardsBox; }
    public Label getActionLabel() { return actionLabel; }
    public Label getChipsLabel() { return chipsLabel; }
    public ImageView getHoleCard1() { return holeCard1; }
    public ImageView getHoleCard2() { return holeCard2; }
    public int getCurrentChips() { return currentChips; }
    public String getCurrentUsername() { return currentUsername; }
}