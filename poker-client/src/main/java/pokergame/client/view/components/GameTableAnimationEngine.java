package pokergame.client.view.components;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
        PlayerSeatController targetSeat = view.getSeatController(action.id());

        if (targetSeat != null) {
            // Trigger dedicated rendering logic inside the sub-controller
            targetSeat.playActionAnimation(action.actionType(), action.amount());
            view.logAction(action.playerId() + ": " + action.actionType() + " ($" + action.amount() + ")");
        }

        // Provide a safety margin for the layout pulse before checking the queue again
        PauseTransition stepDelay = new PauseTransition(Duration.millis(1400));
        stepDelay.setOnFinished(e -> processNextSequence());
        stepDelay.play();
    }
}