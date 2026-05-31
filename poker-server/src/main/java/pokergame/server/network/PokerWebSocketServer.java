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
import pokergame.server.service.TokenValidationService;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class PokerWebSocketServer extends WebSocketServer {

    private final SessionManager sessionManager;
    private final GameCommandProcessor commandProcessor;
    private final TokenValidationService tokenService;
    private final GameNetworkService gameNetworkService;
    private final ObjectMapper objectMapper;

    public PokerWebSocketServer(int port, GameCommandProcessor processor, TokenValidationService tokenService, GameNetworkService gameNetworkService) {
        super(new InetSocketAddress(port));
        this.sessionManager = new SessionManager();
        this.commandProcessor = processor;
        this.tokenService = tokenService;
        this.gameNetworkService = gameNetworkService;
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

        // 2. CONCURRENCY SAFE: Queue the join action. Do not call gameEngine directly!
        commandProcessor.queueCommand(new JoinTableCommand(playerId, buyIn));
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
        try {
            ClientConnection client = sessionManager.getConnectionBySocket(conn);
            if (client == null) return;

            JsonNode rootNode = objectMapper.readTree(message);
            if (!rootNode.has("action")) return;

            String actionType = rootNode.get("action").asText().toUpperCase();
            String playerId = client.getPlayerId();

            // 4. CONCURRENCY SAFE: Convert ALL table interactions into single-threaded commands
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
                case "JOIN_TABLE" -> {
                    JsonNode amtNode = rootNode.get("amount");
                    if (amtNode == null || !amtNode.isInt() || amtNode.asInt() <= 0) {
                        throw new IllegalArgumentException("Buy in amount must be a positive integer");
                    }
                    yield new JoinTableCommand(playerId,  amtNode.asInt());
                }
                case "ADD_BOT" -> new AddBotCommand(playerId);
                case "LEAVE_TABLE" -> new LeaveTableCommand(playerId);
                case "REFRESH_TABLE" -> new RefreshSnapshotCommand(playerId);
                default -> null;
            };

            if (command != null) {
                commandProcessor.queueCommand(command);
            }

        } catch (IllegalArgumentException e) {
            sendErrorToSocket(conn, "Invalid payload configuration: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Malformed data dropped: " + e.getMessage());
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