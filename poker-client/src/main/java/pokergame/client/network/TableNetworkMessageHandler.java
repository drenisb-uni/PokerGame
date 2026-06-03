package pokergame.client.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import pokergame.GameContext;
import pokergame.client.utils.CardParser;
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

    public TableNetworkMessageHandler(GameController controller, GameTableAnimationEngine animationEngine) {
        this.controller = controller;
        this.animationEngine = animationEngine;
        this.eventBusBridge = this::interceptMessageBusEnvelope;

        EventBus.subscribe(GameMessageDTO.class, eventBusBridge);

        // RESTORED: Hydrate UI instantly if a snapshot exists in context from Lobby transition
        Map<String, Object> cachedSnapshot = GameContext.getLastTableSnapshot();
        if (cachedSnapshot != null) {
            applyInitialSnapshot(cachedSnapshot);
        }
    }

    public void cleanup() {
        EventBus.unsubscribe(GameMessageDTO.class, eventBusBridge);
    }

    private void interceptMessageBusEnvelope(GameMessageDTO message) {
        JsonNode payload = mapper.valueToTree(message.payload());

        try {
            switch (message.type()) {
                // 1. INITIAL LOAD ONLY
                case "TABLE_SNAPSHOT", "TARGETED_SNAPSHOT" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> snapshot = mapper.convertValue(payload, Map.class);
                    Platform.runLater(() -> applyInitialSnapshot(snapshot));
                }

                // 2. SURGICAL DELTA UPDATES (Cards & Chips)
                case "HOLE_CARDS_DEALT", "OPPONENT_CARDS_DEALT" -> {
                    String user = payload.get("username").asText();
                    String cards = payload.get("cards").asText();

                    Platform.runLater(() -> controller.updatePlayerHoleCards(user, cards));
                }

                case "POT_UPDATED" -> {
                    int totalPot = payload.get("totalPot").asInt();
                    // FIXED: Method name matches the refactored GameController
                    Platform.runLater(() -> controller.updatePotSize(totalPot));
                }

                case "PLAYER_CHIPS_UPDATED" -> {
                    String user = payload.get("username").asText();
                    int balance = payload.get("newBalance").asInt();
                    Platform.runLater(() -> controller.updatePlayerChips(user, balance));
                }

                case "COMMUNITY_CARDS" -> {
                    JsonNode cardsArray = payload.get("cards");
                    List<Card> receivedCards = CardParser.parseCardsFromJson(cardsArray);
                    Platform.runLater(() -> controller.updateCommunityCards(receivedCards));
                }

                // 3. GAME STATE & TURN CONTROLS (Restored!)
                case "PLAYER_TURN" -> {
                    String activeUser = payload.get("username").asText();
                    int amountToCall = payload.get("amountToCall").asInt();
                    boolean isMe = activeUser.equals(GameContext.getPlayerProfile().username());

                    Platform.runLater(() -> {
                        controller.setTurnControlsEnabled(isMe, amountToCall);
                        controller.setGameStatus(isMe ? "YOUR TURN!" : activeUser + "'s turn...");
                    });
                }

                case "PLAYER_ACTION" -> {
                    HandActionDTO action = mapper.treeToValue(payload, HandActionDTO.class);
                    animationEngine.queueVisualAction(action);
                }

                case "GAME_STATE_CHANGED" -> {
                    String phase = payload.get("state").asText();
                    Platform.runLater(() -> {
                        controller.setGameStatus("Round: " + phase);
                        boolean isHandInProgress = !"WAITING_FOR_PLAYERS".equals(phase) && !"SHOWDOWN".equals(phase);
                        controller.setAdminControlsDisabled(isHandInProgress);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("[Delta Router Error] Failed to route delta event: " + e.getMessage());
        }
    }

    private void applyInitialSnapshot(Map<String, Object> snapshot) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seatsList = (List<Map<String, Object>>) snapshot.get("seats");
        if (seatsList != null) {
            // FIXED: Point to the new layout generator, not the old O(N) loop
            controller.buildInitialTableLayout(seatsList);
        }
    }
}