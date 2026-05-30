package pokergame.server.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer; // This is the library class!
import pokergame.domain.dto.GameMessageDTO;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.server.bot.BotManager;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.PokerGameEngine;
import pokergame.engine.commands.*;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Your custom server class extending the library's abstract WebSocketServer.
 */
public class PokerWebSocketServer extends WebSocketServer implements IGameEventListener {

    private final SessionManager sessionManager;
    private final GameCommandProcessor commandProcessor;
    private final PokerGameEngine gameEngine;
    private final BotManager botManager;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> pendingPlayers = new ConcurrentHashMap<>();
    private final Set<String> pendingLeavers = ConcurrentHashMap.newKeySet();

    // Pass the port up to the super constructor via an InetSocketAddress
    public PokerWebSocketServer(int port, GameCommandProcessor processor, PokerGameEngine gameEngine, BotManager botManager) {
        super(new InetSocketAddress(port));
        this.sessionManager = new SessionManager();
        this.commandProcessor = processor;
        this.gameEngine = gameEngine;
        this.botManager = botManager;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String query = handshake.getResourceDescriptor();
        String playerId = extractPlayerIdFromQuery(query);
        int buyIn = extractIntQueryParam(query, "buyIn", gameEngine.getTableBuyIn());

        if (playerId == null || playerId.isBlank()) {
            conn.close(4001, "Authentication failed: Missing Player ID");
            return;
        }
        if (!gameEngine.canPlayerAffordBuyIn(playerId, buyIn)) {
            conn.close(4002, "Not enough bankroll for this table");
            return;
        }
        sessionManager.registerSession(playerId, conn);
        handlePlayerJoined(playerId, buyIn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        sessionManager.removeSession(conn);
        if (sessionManager.getConnectedPlayerIds().isEmpty()) {
            pendingPlayers.clear();
            pendingLeavers.clear();
            gameEngine.clearTable();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            ClientConnection client = sessionManager.getConnectionBySocket(conn);
            if (client == null) return;

            JsonNode rootNode = objectMapper.readTree(message);
            String actionType = rootNode.get("action").asText().toUpperCase();
            String playerId = client.getPlayerId();

            if ("ADD_BOT".equals(actionType)) {
                handleAddBotRequest();
                return;
            }

            if ("REFRESH_TABLE".equals(actionType)) {
                broadcastTableSnapshot();
                return;
            }

            if ("LEAVE_TABLE".equals(actionType)) {
                handleLeaveTable(conn, playerId);
                return;
            }

            PlayerCommand command = switch (actionType) {
                case "FOLD" -> new FoldCommand(playerId);
                case "CALL" -> new CallCommand(playerId);
                case "RAISE" -> new RaiseCommand(playerId, rootNode.get("amount").asInt());
                default -> null;
            };

            if (command != null) {
                commandProcessor.queueCommand(command);
            } else {
                System.err.println("Unknown command type received: " + actionType);
            }

        } catch (Exception e) {
            System.err.println("Malformed data dropped from socket: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Network socket layer exception: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("Poker WebSocket Server successfully bound and running on port: " + getPort());
    }

    private String extractPlayerIdFromQuery(String resourceDescriptor) {
        String user = extractQueryParam(resourceDescriptor, "user");
        if (user != null && !user.isBlank()) {
            return cleanPlayerId(user);
        }

        return cleanPlayerId(resourceDescriptor.replace("/", "").trim());
    }

    private int extractIntQueryParam(String resourceDescriptor, String name, int fallback) {
        try {
            String value = extractQueryParam(resourceDescriptor, name);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String extractQueryParam(String resourceDescriptor, String name) {
        String query = resourceDescriptor;
        int questionMarkIndex = resourceDescriptor.indexOf('?');
        if (questionMarkIndex >= 0) {
            query = resourceDescriptor.substring(questionMarkIndex + 1);
        } else {
            query = resourceDescriptor.replaceFirst("^/+", "");
        }

        if (!query.contains("=")) {
            return null;
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String cleanPlayerId(String playerId) {
        int extraQueryIndex = playerId.indexOf('&');
        if (extraQueryIndex >= 0) {
            playerId = playerId.substring(0, extraQueryIndex);
        }
        return playerId.trim();
    }

    private void handlePlayerJoined(String playerId, int buyIn) {
        if (sessionManager.getConnectedPlayerIds().size() == 1 && gameEngine.isBetweenHands()) {
            gameEngine.resetTableForBuyIn(buyIn);
        } else {
            gameEngine.configureTableBuyIn(buyIn);
        }

        if (!gameEngine.isBetweenHands() && !gameEngine.hasPlayer(playerId)) {
            pendingPlayers.put(playerId, gameEngine.getTableBuyIn());
            broadcastTableSnapshot();
            return;
        }

        gameEngine.sitPlayerDown(playerId);
        syncWaitingBot();
        broadcastTableSnapshot();
        gameEngine.startHandIfReady();
        broadcastTableSnapshot();
    }

    private void handleAddBotRequest() {
        if (!gameEngine.isBetweenHands() || !gameEngine.hasSeatAvailable()) {
            broadcastTableSnapshot();
            return;
        }

        String botUsername = botManager.nextManualBotName();
        botManager.registerBot(botUsername);
        gameEngine.sitPlayerDown(botUsername);
        broadcastTableSnapshot();
        gameEngine.startHandIfReady();
        broadcastTableSnapshot();
    }

    private void handleLeaveTable(WebSocket conn, String playerId) {
        boolean wasBetweenHands = gameEngine.isBetweenHands();
        gameEngine.leavePlayer(playerId);
        if (!wasBetweenHands) {
            pendingLeavers.add(playerId);
        }
        pendingPlayers.remove(playerId);
        sessionManager.removeSession(conn);
        conn.close(1000, "Left table");

        if (sessionManager.getConnectedPlayerIds().isEmpty()) {
            pendingPlayers.clear();
            pendingLeavers.clear();
            gameEngine.clearTable();
        } else {
            syncWaitingBot();
            broadcastTableSnapshot();
        }
    }

    private void syncWaitingBot() {
        long humanCount = gameEngine.getSeatedUsernames().stream()
                .filter(username -> !botManager.isBot(username))
                .count();

        boolean waitingBotAtTable = gameEngine.hasPlayer(BotManager.WAITING_BOT_USERNAME);

        if (humanCount == 1 && !waitingBotAtTable && gameEngine.hasSeatAvailable()) {
            botManager.registerBot(BotManager.WAITING_BOT_USERNAME);
            gameEngine.sitPlayerDown(BotManager.WAITING_BOT_USERNAME);
            return;
        }

        if (humanCount > 1 && waitingBotAtTable && gameEngine.removePlayer(BotManager.WAITING_BOT_USERNAME)) {
            botManager.unregisterBot(BotManager.WAITING_BOT_USERNAME);
        }
    }

    private void seatPendingPlayers() {
        if (pendingPlayers.isEmpty()) {
            return;
        }

        for (String playerId : List.copyOf(pendingPlayers.keySet())) {
            if (gameEngine.hasSeatAvailable() && !gameEngine.hasPlayer(playerId)) {
                gameEngine.sitPlayerDown(playerId);
            }
            pendingPlayers.remove(playerId);
        }
    }

    private void removePendingLeavers() {
        if (pendingLeavers.isEmpty()) {
            return;
        }

        for (String playerId : List.copyOf(pendingLeavers)) {
            gameEngine.removePlayer(playerId);
            pendingLeavers.remove(playerId);
        }
    }

    private void broadcastTableSnapshot() {
        boolean revealAllCards = gameEngine.getCurrentState() == GameState.HAND_OVER;
        Map<String, Object> sharedPayload = tableSnapshotPayload(null, revealAllCards);
        broadcastEnvelope("TABLE_SNAPSHOT", sharedPayload);

        if (!revealAllCards) {
            for (String playerId : sessionManager.getConnectedPlayerIds()) {
                sendEnvelopeToPlayer(playerId, "TABLE_SNAPSHOT", tableSnapshotPayload(playerId, false));
            }
        }
    }

    private Map<String, Object> tableSnapshotPayload(String viewerUsername, boolean revealAllCards) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("maxSeats", gameEngine.getMaxSeats());
        payload.put("potSize", gameEngine.getPotSize());
        payload.put("gameState", gameEngine.getCurrentState().name());
        payload.put("tableBuyIn", gameEngine.getTableBuyIn());
        payload.put("smallBlind", gameEngine.getSmallBlindAmount());
        payload.put("bigBlind", gameEngine.getBigBlindAmount());
        payload.put("seats", gameEngine.getTableParticipantsForViewer(viewerUsername, revealAllCards));
        return payload;
    }

    private void broadcastEnvelope(String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(new GameMessageDTO(type, "server", payload));
            sessionManager.broadcast(json);
        } catch (Exception e) {
            System.err.println("Failed to broadcast " + type + ": " + e.getMessage());
        }
    }

    private void sendEnvelopeToPlayer(String playerId, String type, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(new GameMessageDTO(type, "server", payload));
            sessionManager.sendToPlayer(playerId, json);
        } catch (Exception e) {
            System.err.println("Failed to send " + type + " to " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public void onGameStateChanged(GameState state) {
        if (state == GameState.HAND_OVER || state == GameState.WAITING_FOR_PLAYERS) {
            removePendingLeavers();
            seatPendingPlayers();
            syncWaitingBot();
        }
        broadcastEnvelope("GAME_STATE", state.name());
        broadcastTableSnapshot();
    }

    @Override
    public void onCommunityCardsDealt(List<Card> cards) {
        broadcastEnvelope("COMMUNITY_CARDS", cards);
    }

    @Override
    public void onNewSeatOccupied(HandParticipantDTO participant) {
        broadcastTableSnapshot();
    }

    @Override
    public void onPlayerTurn(String username, int amountToCall) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("amountToCall", amountToCall);
        broadcastEnvelope("TURN_PROMPT", payload);
    }

    @Override
    public void onPlayerAction(HandActionDTO action) {
        broadcastEnvelope("PLAYER_ACTION", action);
        broadcastTableSnapshot();
    }

    @Override
    public void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("winnerUsernames", winnerUsernames);
        payload.put("winnerHand", winnerHand == null ? null : winnerHand.getType().name());
        payload.put("potSize", potSize);
        broadcastEnvelope("HAND_RESULT", payload);
    }
}
