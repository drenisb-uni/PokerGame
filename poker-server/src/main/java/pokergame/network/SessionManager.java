package pokergame.network;

import org.java_websocket.WebSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    // Thread-safe map matching raw sockets to our custom ClientConnection wrappers
    private final Map<WebSocket, ClientConnection> activeConnections = new ConcurrentHashMap<>();
    // Reverse lookup map to quickly find a connection by player ID
    private final Map<String, ClientConnection> playerConnections = new ConcurrentHashMap<>();

    public void registerSession(String playerId, WebSocket socket) {
        ClientConnection clientConn = new ClientConnection(playerId, socket);
        activeConnections.put(socket, clientConn);
        playerConnections.put(playerId, clientConn);
        System.out.println("Player authenticated and connected: " + playerId);
    }

    public void removeSession(WebSocket socket) {
        ClientConnection conn = activeConnections.remove(socket);
        if (conn != null) {
            playerConnections.remove(conn.getPlayerId());
            System.out.println("Player disconnected: " + conn.getPlayerId());
        }
    }

    public ClientConnection getConnectionBySocket(WebSocket socket) {
        return activeConnections.get(socket);
    }

    /**
     * Sends a message to a single, specific player (perfect for sending private hole cards!).
     */
    public void sendToPlayer(String playerId, String jsonMessage) {
        ClientConnection conn = playerConnections.get(playerId);
        if (conn != null) {
            conn.send(jsonMessage);
        }
    }

    /**
     * Broadcasts a message to absolutely everyone at the table (perfect for game phase changes).
     */
    public void broadcast(String jsonMessage) {
        activeConnections.values().forEach(conn -> conn.send(jsonMessage));
    }
}