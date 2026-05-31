package pokergame.server.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import pokergame.domain.dto.GameMessageDTO;
import pokergame.engine.commands.*;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.service.GameNetworkService;
import pokergame.server.service.LobbyManager;
import pokergame.server.service.TokenValidationService;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class PokerWebSocketServer extends WebSocketServer {

    private final SessionManager sessionManager;
    private final GameCommandProcessor commandProcessor;
    private final TokenValidationService tokenService;
    private final GameNetworkService gameNetworkService;
    private final LobbyManager lobbyManager;
    private final ObjectMapper objectMapper;

    public PokerWebSocketServer(int port, GameCommandProcessor processor, TokenValidationService tokenService, GameNetworkService gameNetworkService, LobbyManager lobbyManager) {
        super(new InetSocketAddress(port));
        this.sessionManager = new SessionManager();
        this.commandProcessor = processor;
        this.tokenService = tokenService;
        this.gameNetworkService = gameNetworkService;
        this.lobbyManager = lobbyManager;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String query = handshake.getResourceDescriptor();

        // 1. SECURE: Extract a cryptographically signed token, NOT a raw username
        String token = extractQueryParam(query, "token");
        int buyIn = extractIntQueryParam(query, "buyIn", 1000);

        String playerId = tokenService.validateTokenAndGetPlayerId(token);
        if (playerId == null) {
            conn.close(4001, "Authentication failed: Invalid or expired token");
            return;
        }

        sessionManager.registerSession(playerId, conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientConnection client = sessionManager.getConnectionBySocket(conn);
        if (client != null) {
            String playerId = client.getPlayerId();
            // 3. CONCURRENCY SAFE: Push the disconnect command to the queue loop
            commandProcessor.queueCommand(new DisconnectPlayerCommand(playerId));
            sessionManager.removeSession(conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[SERVER TRACER] Incoming payload: " + message);

        try {
            ClientConnection client = sessionManager.getConnectionBySocket(conn);
            if (client == null) return;

            JsonNode rootNode = objectMapper.readTree(message);
            if (!rootNode.has("action")) return;

            String actionType = rootNode.get("action").asText().toUpperCase();
            String playerId = client.getPlayerId();

            // Safe extraction of buyIn if provided by the UI, default to 1000
            int buyInAmount = rootNode.has("buyIn") ? rootNode.get("buyIn").asInt() : 1000;

// ==========================================
            // 1. LOBBY ACTIONS (Room Lifecycle)
            // ==========================================
            if (actionType.equals("CREATE_TABLE")) {
                String newTableId = lobbyManager.createNewTable();
                client.setCurrentTableId(newTableId);

                // Confirm room creation to host with their shiny 6-letter code
                GameMessageDTO response = new GameMessageDTO("TABLE_CREATED", Map.of("tableId", newTableId));
                sendMessageToPlayer(playerId, response);

                // Grab the processor for this specific table
                GameCommandProcessor tableProcessor = lobbyManager.getProcessorForTable(newTableId);

                // 1. Queue the host to sit down
                tableProcessor.queueCommand(new JoinTableCommand(playerId, buyInAmount));

                // 2. Extract bot count and queue the bots
                int botCount = rootNode.has("botCount") ? rootNode.get("botCount").asInt() : 0;
                for (int i = 0; i < botCount; i++) {
                    // We use the host's playerId as the requester for adding the bots
                    tableProcessor.queueCommand(new AddBotCommand(playerId));
                }

                // 3. Extract auto-start flag and queue a start command if needed
                boolean startImmediately = rootNode.has("startImmediately") && rootNode.get("startImmediately").asBoolean();
                if (startImmediately) {
                    // If you don't have a StartHandCommand yet, you will need to create a simple one
                    // that tells your engine to begin the betting round!
                    tableProcessor.queueCommand(new StartHandCommand(playerId));
                }

                return;
            }

            if (actionType.equals("JOIN_TABLE")) {
                String targetTableId = rootNode.get("tableId").asText().toUpperCase();

                if (lobbyManager.tableExists(targetTableId)) {
                    client.setCurrentTableId(targetTableId);

                    // Confirm join to client
                    GameMessageDTO response = new GameMessageDTO("TABLE_JOINED", Map.of("tableId", targetTableId));
                    sendMessageToPlayer(playerId, response);

                    // Queue the friend into the specific target table processor
                    lobbyManager.getProcessorForTable(targetTableId)
                            .queueCommand(new JoinTableCommand(playerId, buyInAmount));
                } else {
                    sendErrorToSocket(conn, "Table " + targetTableId + " does not exist.");
                }
                return;
            }

            // ==========================================
            // 2. IN-GAME ACTIONS (Gameplay Guard & Route)
            // ==========================================
            String activeTableId = client.getCurrentTableId();
            if (activeTableId == null) {
                sendErrorToSocket(conn, "Action rejected: You are not currently seated at a table.");
                return;
            }

            GameCommandProcessor activeProcessor = lobbyManager.getProcessorForTable(activeTableId);
            if (activeProcessor == null) {
                sendErrorToSocket(conn, "Action rejected: Your table session has expired or closed.");
                return;
            }

            // Convert table interactions into target-room commands
            PlayerCommand command = switch (actionType) {
                case "FOLD" -> new FoldCommand(playerId);
                case "CALL" -> new CallCommand(playerId);
                case "RAISE" -> {
                    JsonNode amtNode = rootNode.get("amount");
                    if (amtNode == null || !amtNode.isInt() || amtNode.asInt() <= 0) {
                        throw new IllegalArgumentException("Raise amount must be a positive integer");
                    }
                    yield new RaiseCommand(playerId, amtNode.asInt());
                }
                case "ADD_BOT" -> new AddBotCommand(playerId);
                case "LEAVE_TABLE" -> new LeaveTableCommand(playerId);
                case "REFRESH_TABLE" -> new RefreshSnapshotCommand(playerId);
                case "START_HAND" -> new StartHandCommand(playerId);
                default -> null;
            };

            // SUCCESS: Push execution straight to that specific table's single-threaded game loop
            if (command != null) {
                activeProcessor.queueCommand(command);
            } else {
                sendErrorToSocket(conn, "Unknown action command: " + actionType);
            }

        } catch (IllegalArgumentException e) {
            sendErrorToSocket(conn, "Invalid payload configuration: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[WebSocket] Malformed data dropped from client: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void sendErrorToSocket(WebSocket conn, String errorMsg) {
        try {
            String json = objectMapper.writeValueAsString(new GameMessageDTO("ERROR", errorMsg));
            conn.send(json);
        } catch (Exception e) {
            e.printStackTrace();
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

    /**
     * Broadcasts a properly serialized JSON message to ALL connected clients.
     * Use this for global table snapshots and community card reveals.
     */
    public void broadcastMessage(GameMessageDTO message) {
        try {
            // THIS is where the magic happens. Converts the DTO/Map into perfect JSON.
            String jsonString = objectMapper.writeValueAsString(message);

            // Java-WebSocket provides getConnections() natively to broadcast to everyone
            for (WebSocket conn : getConnections()) {
                if (conn != null && conn.isOpen()) {
                    conn.send(jsonString);
                }
            }
        } catch (Exception e) {
            System.err.println("[WebSocket] FATAL: Broadcast serialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends a properly serialized JSON message to a SPECIFIC player.
     * Use this for private messages like hole cards or error notifications.
     */
    public void sendMessageToPlayer(String playerId, GameMessageDTO message) {
        try {
            ClientConnection client = sessionManager.getConnectionByPlayerId(playerId);
            if (client != null && client.getSocket().isOpen()) {

                // Serialize specifically for this player
                String jsonString = objectMapper.writeValueAsString(message);
                client.getSocket().send(jsonString);

            } else {
                System.out.println("[WebSocket] Dropped message for " + playerId + " (Not connected)");
            }
        } catch (Exception e) {
            System.err.println("[WebSocket] FATAL: Targeted message serialization failed for " + playerId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts a specific string query parameter from a URI / resource descriptor.
     * Supports format: "/?token=abc&buyIn=1000" or raw query segments.
     * * @param resourceDescriptor The raw request path/query from the handshake
     * @param key The name of the parameter to fetch (e.g., "token")
     * @return The decoded string value, or null if not found/malformed
     */
    private String extractQueryParam(String resourceDescriptor, String key) {
        if (resourceDescriptor == null || key == null || resourceDescriptor.isBlank()) {
            return null;
        }

        // 1. Isolate the query string part after the '?'
        String queryString = resourceDescriptor;
        int questionMarkIndex = resourceDescriptor.indexOf('?');
        if (questionMarkIndex >= 0) {
            queryString = resourceDescriptor.substring(questionMarkIndex + 1);
        } else {
            // If there's no '?', strip leading slashes just in case the raw string was passed
            queryString = resourceDescriptor.replaceFirst("^/+", "");
        }

        // 2. Split the query string into key-value pairs
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex == -1) {
                continue; // Skip malformed parameters lacking an '=' sign
            }

            String currentKey = pair.substring(0, equalsIndex).trim();
            if (currentKey.equals(key)) {
                String value = pair.substring(equalsIndex + 1);
                // URLDecode the value to handle special characters (like '+' or '%3A' in JWTs) safely
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }

        return null; // Parameter not found
    }

    /**
     * Extracts a specific integer query parameter with a fail-safe fallback.
     * * @param resourceDescriptor The raw request path/query from the handshake
     * @param key The name of the parameter to fetch (e.g., "buyIn")
     * @param fallback The default value to return if parsing fails or parameter is missing
     * @return The parsed integer or the fallback value
     */
    private int extractIntQueryParam(String resourceDescriptor, String key, int fallback) {
        String valueStr = extractQueryParam(resourceDescriptor, key);
        if (valueStr == null || valueStr.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(valueStr.trim());
        } catch (NumberFormatException e) {
            // Log a warning if a user passes garbage data like "buyIn=twenty-five-hundred"
            System.err.println("[Network Warning] Failed to parse integer parameter '" + key + "' with value: '" + valueStr + "'. Falling back to: " + fallback);
            return fallback;
        }
    }
}