package pokergame.client.view.controllers.table;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;

public class PlayerSeatController {

    @FXML private VBox playerBox;
    @FXML private Label usernameLabel;
    @FXML private Label chipsLabel;
    @FXML private Label actionLabel;
    @FXML private HBox cardsBox;
    @FXML private ImageView holeCard1;
    @FXML private ImageView holeCard2;

    private static final String CARD_BACK = "/images/BACK.png";
    private int currentChips;

    public void setup(HandParticipantDTO seat) {
        usernameLabel.setText(seat.playerUsername());
        updateChips(seat.startChips());
        actionLabel.setText("");
        resetSeatVisuals();
        showCardBacks();
    }

    public void setup(HandParticipantDTO seat, String displayName) {
        usernameLabel.setText(displayName);
        this.currentChips = 0;
        updateChips(seat.startChips());
        actionLabel.setText("");
        resetSeatVisuals();
        showCardBacks();
    }

    public void updateChips(int amount) {
        int previousChips = currentChips;
        currentChips = amount;
        chipsLabel.setText("$" + amount);
        if (previousChips != 0 && previousChips != amount) {
            playChipPulse(amount > previousChips);
        }
    }

    public void setAction(String action) {
        actionLabel.setText(action);
    }

    public void restoreActionVisual(String action) {
        setAction(action);
        String normalizedAction = action == null ? "" : action.toUpperCase();

        if (normalizedAction.contains("FOLDED")) {
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
        playCardsRevealAnimation();
    }

    public void showCardBacks() {
        Image back = new Image(getClass().getResource(CARD_BACK).toExternalForm());
        holeCard1.setImage(back);
        holeCard2.setImage(back);
        resetCardsVisuals();
    }

    public void clearCards() {
        holeCard1.setImage(null);
        holeCard2.setImage(null);
    }

    public void playSeatEntryAnimation() {
        resetSeatVisuals();
        playerBox.setOpacity(0);
        playerBox.setTranslateY(24);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(420), playerBox);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(420), playerBox);
        slideIn.setToY(0);

        new ParallelTransition(fadeIn, slideIn).play();
    }

    public void setTurnActive(boolean active) {
        setStyleClass(playerBox, "player-active", active);
        if (!active) {
            playerBox.setScaleX(1);
            playerBox.setScaleY(1);
            return;
        }

        ScaleTransition pulseUp = new ScaleTransition(Duration.millis(260), playerBox);
        pulseUp.setToX(1.07);
        pulseUp.setToY(1.07);

        ScaleTransition pulseDown = new ScaleTransition(Duration.millis(260), playerBox);
        pulseDown.setToX(1);
        pulseDown.setToY(1);

        new SequentialTransition(pulseUp, pulseDown).play();
    }

    public void playActionHandoffIn(String actionText, String actionType) {
        setTurnActive(true);
        setStyleClass(playerBox, "player-action-focus", true);
        actionLabel.setText(actionText);
        applyActionStyle(actionType);
        playActionBadgeAnimation();

        TranslateTransition lift = new TranslateTransition(Duration.millis(520), playerBox);
        lift.setToY(-10);

        ScaleTransition grow = new ScaleTransition(Duration.millis(520), playerBox);
        grow.setToX(1.08);
        grow.setToY(1.08);

        new ParallelTransition(lift, grow).play();
    }

    public void playActionHandoffOut() {
        setStyleClass(playerBox, "player-action-focus", false);

        TranslateTransition settle = new TranslateTransition(Duration.millis(520), playerBox);
        settle.setToY(0);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(520), playerBox);
        shrink.setToX(1);
        shrink.setToY(1);

        new ParallelTransition(settle, shrink).play();
    }

    public void playActionAnimation(String actionType, int amount) {
        String normalizedAction = normalizeAction(actionType);
        playerBox.getStyleClass().remove("player-raised");
        playerBox.getStyleClass().remove("player-action-focus");
        applyActionStyle(normalizedAction);
        playActionBadgeAnimation();

        PauseTransition readabilityPause = new PauseTransition(Duration.millis(650));
        readabilityPause.setOnFinished(event -> {
            switch (normalizedAction) {
                case "FOLD" -> playFoldAnimation();
                case "CALL", "CHECK" -> playSeatPulse(1.04, 260);
                case "RAISE", "ALL_IN" -> playRaiseAnimation(amount);
                default -> playSeatPulse(1.03, 220);
            }
        });
        readabilityPause.play();
    }

    public void playWinnerAnimation(int potSize) {
        resetSeatVisuals();
        setStyleClass(playerBox, "player-winner", true);
        actionLabel.setText(potSize > 0 ? "WINNER +$" + potSize : "WINNER");
        applyActionStyle("WIN");

        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(520), playerBox);
        scaleUp.setToX(1.18);
        scaleUp.setToY(1.18);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(520), playerBox);
        scaleDown.setToX(1);
        scaleDown.setToY(1);

        TranslateTransition lift = new TranslateTransition(Duration.millis(520), playerBox);
        lift.setByY(-14);
        lift.setAutoReverse(true);
        lift.setCycleCount(4);

        ScaleTransition badgePulse = new ScaleTransition(Duration.millis(520), actionLabel);
        badgePulse.setToX(1.34);
        badgePulse.setToY(1.34);
        badgePulse.setAutoReverse(true);
        badgePulse.setCycleCount(6);

        SequentialTransition pulse = new SequentialTransition(scaleUp, scaleDown);
        pulse.setCycleCount(4);

        PauseTransition holdWinner = new PauseTransition(Duration.millis(3200));
        holdWinner.setOnFinished(event -> playerBox.getStyleClass().remove("player-winner"));

        new SequentialTransition(new ParallelTransition(pulse, lift, badgePulse), holdWinner).play();
    }

    private void resetSeatVisuals() {
        playerBox.setOpacity(1);
        playerBox.setScaleX(1);
        playerBox.setScaleY(1);
        playerBox.setTranslateY(0);
        setTurnActive(false);
        playerBox.getStyleClass().remove("player-folded");
        playerBox.getStyleClass().remove("player-raised");
        playerBox.getStyleClass().remove("player-action-focus");
        playerBox.getStyleClass().remove("player-winner");
        resetCardsVisuals();
    }

    private void resetCardsVisuals() {
        cardsBox.setOpacity(1);
        cardsBox.setTranslateY(0);
        cardsBox.setRotate(0);
    }

    private void playActionBadgeAnimation() {
        actionLabel.setOpacity(0);
        actionLabel.setScaleX(0.72);
        actionLabel.setScaleY(0.72);
        actionLabel.setTranslateY(12);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(260), actionLabel);
        fadeIn.setToValue(1);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(320), actionLabel);
        scaleIn.setToX(1.18);
        scaleIn.setToY(1.18);

        TranslateTransition lift = new TranslateTransition(Duration.millis(320), actionLabel);
        lift.setToY(0);

        PauseTransition hold = new PauseTransition(Duration.millis(260));

        ScaleTransition settle = new ScaleTransition(Duration.millis(240), actionLabel);
        settle.setToX(1);
        settle.setToY(1);

        new SequentialTransition(new ParallelTransition(fadeIn, scaleIn, lift), hold, settle).play();
    }

    private void playFoldAnimation() {
        setStyleClass(playerBox, "player-folded", true);

        FadeTransition fadeCards = new FadeTransition(Duration.millis(1200), cardsBox);
        fadeCards.setToValue(0.15);

        TranslateTransition dropCards = new TranslateTransition(Duration.millis(1200), cardsBox);
        dropCards.setToY(34);

        FadeTransition fadeSeat = new FadeTransition(Duration.millis(1200), playerBox);
        fadeSeat.setToValue(0.38);

        ScaleTransition badgePulse = new ScaleTransition(Duration.millis(420), actionLabel);
        badgePulse.setToX(1.32);
        badgePulse.setToY(1.32);
        badgePulse.setAutoReverse(true);
        badgePulse.setCycleCount(2);

        RotateTransition tiltCards = new RotateTransition(Duration.millis(1200), cardsBox);
        tiltCards.setToAngle(-10);

        new ParallelTransition(fadeCards, dropCards, fadeSeat, badgePulse, tiltCards).play();
    }

    private void playRaiseAnimation(int amount) {
        setStyleClass(playerBox, "player-raised", true);
        playSeatPulse(1.18, 520);

        TranslateTransition cardsKick = new TranslateTransition(Duration.millis(520), cardsBox);
        cardsKick.setByY(-22);
        cardsKick.setAutoReverse(true);
        cardsKick.setCycleCount(2);

        ScaleTransition amountPulseUp = new ScaleTransition(Duration.millis(520), actionLabel);
        amountPulseUp.setToX(amount > 0 ? 1.45 : 1.25);
        amountPulseUp.setToY(amount > 0 ? 1.45 : 1.25);

        ScaleTransition amountHold = new ScaleTransition(Duration.millis(720), actionLabel);
        amountHold.setToX(amount > 0 ? 1.24 : 1.12);
        amountHold.setToY(amount > 0 ? 1.24 : 1.12);

        ScaleTransition amountSettle = new ScaleTransition(Duration.millis(520), actionLabel);
        amountSettle.setToX(1);
        amountSettle.setToY(1);

        PauseTransition settleGlow = new PauseTransition(Duration.millis(1600));
        settleGlow.setOnFinished(event -> playerBox.getStyleClass().remove("player-raised"));

        new ParallelTransition(
                new SequentialTransition(amountPulseUp, amountHold, amountSettle),
                new SequentialTransition(cardsKick, settleGlow)
        ).play();
    }

    private void playSeatPulse(double scale, int millis) {
        ScaleTransition pulseUp = new ScaleTransition(Duration.millis(millis), playerBox);
        pulseUp.setToX(scale);
        pulseUp.setToY(scale);

        ScaleTransition pulseDown = new ScaleTransition(Duration.millis(millis), playerBox);
        pulseDown.setToX(1);
        pulseDown.setToY(1);

        new SequentialTransition(pulseUp, pulseDown).play();
    }

    private void playChipPulse(boolean gainedChips) {
        setStyleClass(chipsLabel, "chips-gained", gainedChips);
        setStyleClass(chipsLabel, "chips-spent", !gainedChips);

        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(240), chipsLabel);
        scaleUp.setToX(1.18);
        scaleUp.setToY(1.18);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(260), chipsLabel);
        scaleDown.setToX(1);
        scaleDown.setToY(1);

        PauseTransition cleanup = new PauseTransition(Duration.millis(620));
        cleanup.setOnFinished(event -> {
            chipsLabel.getStyleClass().remove("chips-gained");
            chipsLabel.getStyleClass().remove("chips-spent");
        });

        new SequentialTransition(scaleUp, scaleDown, cleanup).play();
    }

    private void playCardsRevealAnimation() {
        for (ImageView cardView : new ImageView[]{ holeCard1, holeCard2 }) {
            cardView.setOpacity(0);
            cardView.setScaleX(0.55);
            cardView.setScaleY(0.55);
            cardView.setRotate(-7);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(320), cardView);
            fadeIn.setToValue(1);

            ScaleTransition scaleIn = new ScaleTransition(Duration.millis(420), cardView);
            scaleIn.setToX(1);
            scaleIn.setToY(1);

            TranslateTransition settle = new TranslateTransition(Duration.millis(420), cardView);
            settle.setFromY(-10);
            settle.setToY(0);

            ParallelTransition reveal = new ParallelTransition(fadeIn, scaleIn, settle);
            reveal.setOnFinished(event -> cardView.setRotate(0));
            reveal.play();
        }
    }

    private void applyActionStyle(String actionType) {
        actionLabel.getStyleClass().removeAll(
                "action-badge",
                "action-fold",
                "action-call",
                "action-check",
                "action-raise",
                "action-pending",
                "action-win"
        );
        actionLabel.getStyleClass().add("action-badge");

        switch (actionType) {
            case "FOLD" -> actionLabel.getStyleClass().add("action-fold");
            case "CALL" -> actionLabel.getStyleClass().add("action-call");
            case "CHECK" -> actionLabel.getStyleClass().add("action-check");
            case "RAISE", "ALL_IN" -> actionLabel.getStyleClass().add("action-raise");
            case "PENDING" -> actionLabel.getStyleClass().add("action-pending");
            case "WIN" -> actionLabel.getStyleClass().add("action-win");
            default -> {
            }
        }
    }

    private String normalizeAction(String actionType) {
        if (actionType == null) {
            return "";
        }
        return actionType.trim().toUpperCase().replace(' ', '_');
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

    public int getCurrentChips() {
        return currentChips;
    }
}
