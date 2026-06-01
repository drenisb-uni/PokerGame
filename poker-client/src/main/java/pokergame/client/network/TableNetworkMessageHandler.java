package pokergame.client.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import pokergame.GameContext;
import pokergame.client.utils.EventBus;
import pokergame.client.view.components.GameTableAnimationEngine;
import pokergame.client.view.controllers.table.GameController;
import pokergame.domain.dto.GameMessageDTO;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.model.Card;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TableNetworkMessageHandler {

    private final GameController controller;
    private final GameTableAnimationEngine animationEngine;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Consumer<GameMessageDTO> eventBusBridge;
    private Map<String, Object> cachedSnapshot = null;

    public TableNetworkMessageHandler(GameController controller, GameTableAnimationEngine animationEngine) {
        this.controller = controller;
        this.animationEngine = animationEngine;
        this.eventBusBridge = this::interceptMessageBusEnvelope;

        // Subscribe using an explicit consumer reference instance
        EventBus.subscribe(GameMessageDTO.class, eventBusBridge);
        this.animationEngine.setOnQueueDrained(this::flushDeferredSnapshot);

        Map<String, Object> cachedSnapshot = GameContext.getLastTableSnapshot();
        if (cachedSnapshot != null) {
            System.out.println("[UI Controller] Found cached table layout. Performing initial draw...");
            evaluateSnapshotRouting(cachedSnapshot);
        }
    }

    /**
     * Call this when leaving the table to prevent duplicate event loops.
     */
    public void cleanup() {
        EventBus.unsubscribe(GameMessageDTO.class, eventBusBridge);
    }

    private void interceptMessageBusEnvelope(GameMessageDTO message) {
        JsonNode payloadNode = mapper.valueToTree(message.payload());
        processInboundPacket(message.type(), payloadNode);
    }

    public void processInboundPacket(String type, JsonNode payload) {
        try {
            switch (type) {
                case "TABLE_SNAPSHOT", "TARGETED_SNAPSHOT" -> {
                    Map<String, Object> snapshot = mapper.convertValue(payload, Map.class);
                    evaluateSnapshotRouting(snapshot);
                }
                case "PLAYER_ACTION" -> {
                    HandActionDTO action = mapper.treeToValue(payload, HandActionDTO.class);
                    animationEngine.queueVisualAction(action);
                }
                case "PLAYER_TURN" -> {
                    String activeUser = payload.get("username").asText();
                    int amountToCall = payload.get("amountToCall").asInt();
                    boolean isMe = activeUser.equals(GameContext.getPlayerProfile().username());

                    Platform.runLater(() -> {
                        controller.setTurnControlsEnabled(isMe, amountToCall);
                        controller.setGameStatus(isMe ? "YOUR TURN!" : activeUser + "'s turn...");
                    });
                }
                case "GAME_STATE_CHANGED" -> {
                    String phase = payload.get("state").asText();
                    Platform.runLater(() -> {
                        controller.setGameStatus("Round: " + phase);
                        controller.updateCommunityCards();

                        // Disable admin actions like adding bots if a hand is mid-progress
                        boolean isHandInProgress = !"WAITING_FOR_PLAYERS".equals(phase) && !"SHOWDOWN".equals(phase);
                        controller.setAdminControlsDisabled(isHandInProgress);
                    });
                }
                case "COMMUNITY_CARDS_DEALT" -> {
//                    JsonNode cardsArray = payload.get("cards"); // Assumes server wraps them in a "cards" array
//                    List<Card> receivedCards = parseCardsFromJson(cardsArray);
//
//                    Platform.runLater(() -> {
//                        // Now you are actually passing the server's cards to your controller!
//                        controller.updateCommunityCards(receivedCards);
//                    });
                }
            }
        } catch (Exception e) {
            System.err.println("[Decoder Error] Failed to map variant packet: " + e.getMessage());
        }
    }

    private void evaluateSnapshotRouting(Map<String, Object> snapshot) {
        String state = (String) snapshot.get("gameState");

        if ("WAITING_FOR_PLAYERS".equals(state) || !animationEngine.isWorking()) {
            applySnapshotToUI(snapshot);
            return;
        }

        this.cachedSnapshot = snapshot;
    }

    private void flushDeferredSnapshot() {
        if (cachedSnapshot != null) {
            Map<String, Object> targeting = cachedSnapshot;
            cachedSnapshot = null;
            applySnapshotToUI(targeting);
        }
    }

    private void applySnapshotToUI(Map<String, Object> snapshot) {
        Platform.runLater(() -> {
            System.out.println("[TableNetworkMessageHandler] Apply Snapshot");
            int pot = (int) snapshot.get("potSize");
            List<Map<String, Object>> seatsList = (List<Map<String, Object>>) snapshot.get("seats");
            String selfUser = GameContext.getPlayerProfile().username();
            int myBalance = 0;

            for (Map<String, Object> seat : seatsList) {
                if (selfUser.equals(seat.get("playerUsername"))) {
                    int endChips = (int) seat.get("endChips");
                    // Fall back to startChips if endChips hasn't been set yet
                    myBalance = endChips > 0 ? endChips : (int) seat.get("startChips");
                    break;
                }
            }

            controller.updateChipsAndPotDisplay(myBalance, pot);
            controller.syncSeatsLayout(seatsList);
        });
    }
}