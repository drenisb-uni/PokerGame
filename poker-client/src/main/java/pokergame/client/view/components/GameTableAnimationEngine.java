package pokergame.client.view.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import pokergame.client.view.controllers.table.GameController;
import pokergame.client.view.controllers.table.PlayerSeatController;
import pokergame.domain.dto.HandActionDTO;

import java.util.LinkedList;
import java.util.Queue;

public class GameTableAnimationEngine {

    private final GameController view;
    private final Queue<HandActionDTO> queue = new LinkedList<>();
    private boolean isAnimating = false;
    private Runnable onDrainedCallback;

    public GameTableAnimationEngine(GameController view) {
        this.view = view;
    }

    public void setOnQueueDrained(Runnable callback) {
        this.onDrainedCallback = callback;
    }

    public synchronized void queueVisualAction(HandActionDTO action) {
        queue.offer(action);
        if (!isAnimating) {
            processNextSequence();
        }
    }

    public boolean isWorking() {
        return isAnimating || !queue.isEmpty();
    }

    private synchronized void processNextSequence() {
        HandActionDTO next = queue.poll();
        if (next == null) {
            isAnimating = false;
            if (onDrainedCallback != null) {
                Platform.runLater(onDrainedCallback);
            }
            return;
        }

        isAnimating = true;
        Platform.runLater(() -> executeSeatAnimation(next));
    }

    private void executeSeatAnimation(HandActionDTO action) {
        // Locate target controller dynamically from layout references
        PlayerSeatController targetSeat = view.getSeatControllerByUsername(action.playerId());

        if (targetSeat != null) {
            // Trigger dedicated rendering logic inside the sub-controller
        }

        // Provide a safety margin for the layout pulse before checking the queue again
        PauseTransition stepDelay = new PauseTransition(Duration.millis(1400));
        stepDelay.setOnFinished(e -> processNextSequence());
        stepDelay.play();
    }

    public static void playFoldSequence(PlayerSeatController controller) {
        VBox playerBox = controller.getPlayerBox();
        HBox cardsBox = controller.getCardsBox();
        Label actionLabel = controller.getActionLabel();

        if (!playerBox.getStyleClass().contains("player-folded")) {
            playerBox.getStyleClass().add("player-folded");
        }

        FadeTransition fadeCards = new FadeTransition(Duration.millis(800), cardsBox);
        fadeCards.setToValue(0.20);

        TranslateTransition dropCards = new TranslateTransition(Duration.millis(800), cardsBox);
        dropCards.setToY(30);

        RotateTransition tiltCards = new RotateTransition(Duration.millis(800), cardsBox);
        tiltCards.setToAngle(-12);

        FadeTransition fadeSeat = new FadeTransition(Duration.millis(800), playerBox);
        fadeSeat.setToValue(0.45);

        new ParallelTransition(fadeCards, dropCards, tiltCards, fadeSeat).play();
    }

    public static void playActiveTurnPulse(PlayerSeatController controller, boolean active) {
        VBox playerBox = controller.getPlayerBox();
        playerBox.getStyleClass().remove("player-active");

        if (!active) {
            playerBox.setScaleX(1.0);
            playerBox.setScaleY(1.0);
            return;
        }

        playerBox.getStyleClass().add("player-active");

        ScaleTransition pulseUp = new ScaleTransition(Duration.millis(300), playerBox);
        pulseUp.setToX(1.05);
        pulseUp.setToY(1.05);

        ScaleTransition pulseDown = new ScaleTransition(Duration.millis(300), playerBox);
        pulseDown.setToX(1.0);
        pulseDown.setToY(1.0);

        SequentialTransition loop = new SequentialTransition(pulseUp, pulseDown);
        loop.setCycleCount(Animation.INDEFINITE);
        loop.play();

        // Tag timeline to node properties so we can kill it when turn advances
        playerBox.setUserData(loop);
    }

    public static void stopTurnPulse(PlayerSeatController controller) {
        VBox playerBox = controller.getPlayerBox();
        if (playerBox.getUserData() instanceof SequentialTransition anim) {
            anim.stop();
        }
        playActiveTurnPulse(controller, false);
    }

    public static void playChipPulse(Label chipsLabel, boolean gainedChips) {
        chipsLabel.getStyleClass().removeAll("chips-gained", "chips-spent");
        chipsLabel.getStyleClass().add(gainedChips ? "chips-gained" : "chips-spent");

        ScaleTransition pulse = new ScaleTransition(Duration.millis(200), chipsLabel);
        pulse.setToX(1.2);
        pulse.setToY(1.2);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);

        pulse.setOnFinished(e -> {
            chipsLabel.getStyleClass().removeAll("chips-gained", "chips-spent");
        });
        pulse.play();
    }

    public static void playHoleCardsReveal(PlayerSeatController controller) {
        Node[] cards = new Node[]{ controller.getHoleCard1(), controller.getHoleCard2() };

        for (Node card : cards) {
            card.setOpacity(0.0);
            card.setScaleX(0.4);
            card.setScaleY(0.4);

            FadeTransition show = new FadeTransition(Duration.millis(350), card);
            show.setToValue(1.0);

            ScaleTransition grow = new ScaleTransition(Duration.millis(350), card);
            grow.setToX(1.0);
            grow.setToY(1.0);

            new ParallelTransition(show, grow).play();
        }
    }

    public static void playSeatEntryAnimation(PlayerSeatController controller) {
        var box = controller.getPlayerBox();
        box.setOpacity(0.0);
        box.setTranslateY(20);

        javafx.animation.FadeTransition fi = new javafx.animation.FadeTransition(javafx.util.Duration.millis(350), box);
        fi.setToValue(1.0);

        javafx.animation.TranslateTransition si = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(350), box);
        si.setToY(0);

        new javafx.animation.ParallelTransition(fi, si).play();
    }
}