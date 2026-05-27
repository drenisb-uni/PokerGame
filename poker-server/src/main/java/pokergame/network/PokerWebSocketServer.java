package pokergame.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer; // This is the library class!
import pokergame.engine.GameCommandProcessor;
import pokergame.engine.commands.*;

import java.net.InetSocketAddress;

/**
 * Your custom server class extending the library's abstract WebSocketServer.
 */
public class PokerWebSocketServer extends WebSocketServer {

    private final SessionManager sessionManager;
    private final GameCommandProcessor commandProcessor;
    private final ObjectMapper objectMapper;

    // Pass the port up to the super constructor via an InetSocketAddress
    public PokerWebSocketServer(int port, GameCommandProcessor processor) {
        super(new InetSocketAddress(port));
        this.sessionManager = new SessionManager();
        this.commandProcessor = processor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String query = handshake.getResourceDescriptor();
        String playerId = extractPlayerIdFromQuery(query);

        if (playerId == null || playerId.isBlank()) {
            conn.close(4001, "Authentication failed: Missing Player ID");
            return;
        }
        sessionManager.registerSession(playerId, conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        sessionManager.removeSession(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            ClientConnection client = sessionManager.getConnectionBySocket(conn);
            if (client == null) return;

            JsonNode rootNode = objectMapper.readTree(message);
            String actionType = rootNode.get("action").asText().toUpperCase();
            String playerId = client.getPlayerId();

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
        if (resourceDescriptor.contains("=")) {
            return resourceDescriptor.substring(resourceDescriptor.indexOf("=") + 1);
        }
        return resourceDescriptor.replace("/", "").trim();
    }
}