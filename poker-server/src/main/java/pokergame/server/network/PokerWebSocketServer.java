package pokergame.server.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import pokergame.domain.dto.GameMessageDTO;
import pokergame.server.engine.actor.TableActor;
import pokergame.server.engine.actor.messages.PlayerActionMessage;
import pokergame.server.service.LobbyManager;
import pokergame.server.service.TokenValidationService;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class PokerWebSocketServer extends WebSocketServer {

    private final SessionManager sessionManager;
    private final TokenValidationService tokenService;
    private final LobbyManager lobbyManager;
    private final ObjectMapper objectMapper;

    public PokerWebSocketServer(int port, TokenValidationService tokenService, LobbyManager lobbyManager) {
        super(new InetSocketAddress(port));
        this.sessionManager = new SessionManager();
        this.tokenService = tokenService;
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
        try {
            ClientConnection client = sessionManager.getConnectionBySocket(conn);
            if (client == null) return;

            String playerId = client.getPlayerId();
            String activeTableId = client.getCurrentTableId();

            System.out.println("[WebSocket Disconnect] Player " + playerId + " left. Code: " + code + ", Reason: " + reason);

            if (activeTableId != null) {
                TableActor tableActor = lobbyManager.getActorForTable(activeTableId);

                if (tableActor != null) {
                    // 2. CONCURRENCY SAFE: Dispatch a non-blocking disconnect notification to the loop mailbox.
                    tableActor.tell(new PlayerActionMessage(playerId, "LEAVE_TABLE", 0));
                    this.sessionManager.unbindPlayerFromTableRoom(playerId, activeTableId);

                    // 3. LIFECYCLE MANAGEMENT: Clean up empty tables to avoid memory leaks.
                    if (tableActor.getHumanPlayerCount() <= 1) {
                        System.out.println("[Lobby Supervisor] Table " + activeTableId + " is now empty. Initiating teardown...");
                        lobbyManager.destroyTable(activeTableId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[WebSocket Error] Exception during player connection cleanup: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 4. ABSOLUTE GUARANTEE: Remove the web socket session mapping from memory
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
                // Spawns and registers the thread-confined TableActor
                String newTableId = lobbyManager.createNewTable();
                conn.setAttachment(newTableId);
                client.setCurrentTableId(newTableId);

                // 👉 CHANGE: Securely bind the host player to the room roster FIRST
                sessionManager.bindPlayerToTableRoom(playerId, newTableId);

                // Confirm room creation to host with their 6-letter code
                GameMessageDTO response = new GameMessageDTO("TABLE_CREATED", Map.of("tableId", newTableId));

                // 👉 WHY: This will now succeed because the room roster contains the playerId!
                broadcastToTablePlayer(newTableId, playerId, response);

                // Grab the running Actor boundary for this specific table
                TableActor tableActor = lobbyManager.getActorForTable(newTableId);
                if (tableActor == null) {
                    sendErrorToSocket(conn, "Critical Error: Failed to start table session actor.");
                    return;
                }

                // Queue the host to join via Actor Message Protocol
                tableActor.tell(new PlayerActionMessage(playerId, "JOIN_TABLE", buyInAmount));

                // Extract bot count and dispatch individual addition messages down to the loop
                int botCount = rootNode.has("botCount") ? rootNode.get("botCount").asInt() : 0;
                for (int i = 0; i < botCount; i++) {
                    tableActor.tell(new PlayerActionMessage(playerId, "ADD_BOT", 0));
                }

                return;
            }

            if (actionType.equals("JOIN_TABLE")) {
                String targetTableId = rootNode.get("tableId").asText().toUpperCase();

                // Validate that the Actor exists under the Lobby Supervisor manager registry
                TableActor tableActor = lobbyManager.getActorForTable(targetTableId);

                if (tableActor != null) {
                    client.setCurrentTableId(targetTableId);

                    // 👉 CHANGE: Securely bind the joining player to the room roster FIRST
                    this.sessionManager.bindPlayerToTableRoom(playerId, targetTableId);
                    System.out.println("[WebSocket] Linked connection for " + playerId + " to room tracking registry: " + targetTableId);

                    // Confirm join approval back to client socket
                    GameMessageDTO response = new GameMessageDTO("TABLE_JOINED", Map.of("tableId", targetTableId));

                    // 👉 CHANGE: Swapped out raw conn.send for the refactored, unified broadcaster method
                    broadcastToTablePlayer(targetTableId, playerId, response);

                    // Safe Async Ingestion: Drop message into the targeted room Actor Mailbox
                    tableActor.tell(new PlayerActionMessage(playerId, "JOIN_TABLE", buyInAmount));
                } else {
                    sendErrorToSocket(conn, "Table " + targetTableId + " does not exist or has expired.");
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

            // ARCHITECTURE REFACTOR: Fetch the isolated TableActor instead of the old shared processor
            TableActor tableActor = lobbyManager.getActorForTable(activeTableId);
            if (tableActor == null) {
                sendErrorToSocket(conn, "Action rejected: Your table session has expired or closed.");
                return;
            }

            // Extract amount safely. Defaults to 0 if not provided (e.g., for FOLD or CALL)
            int amount = 0;
            JsonNode amtNode = rootNode.get("amount");
            if (amtNode != null && amtNode.isInt()) {
                amount = amtNode.asInt();
            }

            // Gateway validation: Fail fast on obviously bad network data before consuming actor mailbox capacity
            if (("RAISE".equals(actionType) || "ALL_IN".equals(actionType)) && amount <= 0) {
                throw new IllegalArgumentException("Raise amount must be a positive integer.");
            }

            // SUCCESS: Fire-and-forget message passing!
            // The Actor thread handles ALL validation and execution sequencing safely within its boundary.
            tableActor.tell(new PlayerActionMessage(playerId, actionType, amount));

        } catch (IllegalArgumentException e) {
            sendErrorToSocket(conn, "Invalid payload configuration: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[WebSocket] Malformed data dropped from client: " + e.getMessage());
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

    public void broadcastToTablePlayer(String targetTableId, String playerId, GameMessageDTO message) {
        if (targetTableId == null || targetTableId.isBlank()) {
            System.err.println("[WebSocket Broadcaster] Aborted targeted message: targetTableId is null or empty.");
            return;
        }
        try {
            // 1. Verify table layout occupancy directly using the clear roster map instead of attachments
            java.util.Set<String> roomRoster = sessionManager.getPlayersInRoom(targetTableId);

            if (!roomRoster.contains(playerId)) {
                System.out.println("[WebSocket] Dropped targeted message for " + playerId + " (Player is not recorded at table " + targetTableId + ")");
                return;
            }

            // 2. Fetch the client connection wrapper seamlessly by Player Id reference
            ClientConnection client = sessionManager.getConnectionByPlayerId(playerId);

            if (client != null && client.getSocket() != null && client.getSocket().isOpen()) {
                String jsonString = objectMapper.writeValueAsString(message);
                client.getSocket().send(jsonString);
            } else {
                System.out.println("[WebSocket] Dropped message for " + playerId + " (Not connected or connection dead)");
            }
        } catch (Exception e) {
            System.err.println("[WebSocket] FATAL: Targeted message serialization/sending failed for " + playerId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcastToTable(String targetTableId, GameMessageDTO message) {
        if (targetTableId == null || targetTableId.isBlank()) {
            System.err.println("[WebSocket Broadcaster] Aborted broadcast: targetTableId is null or empty.");
            return;
        }

        try {
            // 1. Pull the unique list of players assigned to this room context
            java.util.Set<String> roomRoster = sessionManager.getPlayersInRoom(targetTableId);
            if (roomRoster.isEmpty()) return;

            String jsonMessage = objectMapper.writeValueAsString(message);

            // 2. Optimization: Loop directly through the room's targeted users instead of scanning every player globally!
            for (String playerId : roomRoster) {
                ClientConnection client = sessionManager.getConnectionByPlayerId(playerId);

                if (client != null && client.getSocket() != null && client.getSocket().isOpen()) {
                    client.getSocket().send(jsonMessage);
                }
            }
        } catch (Exception e) {
            System.err.println("[WebSocket Broadcaster] Failed to serialize or broadcast message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcastToTableExcluding(String tableId, String username, GameMessageDTO broadcast) {
    }

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

    private void sendErrorToSocket(WebSocket conn, String errorMsg) {
        try {
            String json = objectMapper.writeValueAsString(new GameMessageDTO("ERROR", errorMsg));
            conn.send(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}