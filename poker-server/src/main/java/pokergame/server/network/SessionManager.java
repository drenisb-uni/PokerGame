package pokergame.server.network;

import org.java_websocket.WebSocket;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    // Thread-safe map matching raw sockets to our custom ClientConnection wrappers
    private final Map<WebSocket, ClientConnection> activeConnections = new ConcurrentHashMap<>();
    // Reverse lookup map to quickly find a connection by player ID
    private final Map<String, ClientConnection> playerConnections = new ConcurrentHashMap<>();

    // NEW ROOM TOPOLOGY: Maps TableId -> Set of PlayerIds cleanly across threads
    private final Map<String, Set<String>> tableRoomRosters = new ConcurrentHashMap<>();

    public void registerSession(String playerId, WebSocket socket) {
        ClientConnection clientConn = new ClientConnection(playerId, socket);
        activeConnections.put(socket, clientConn);
        playerConnections.put(playerId, clientConn);
        System.out.println("Player authenticated and connected: " + playerId);
    }

    public void removeSession(WebSocket socket) {
        ClientConnection conn = activeConnections.remove(socket);
        if (conn != null) {
            String playerId = conn.getPlayerId();
            playerConnections.remove(playerId);

            // FIX: Clean up any active room rosters so disconnected ghost sessions vanish
            removePlayerFromAllRooms(playerId);
            System.out.println("Player disconnected: " + playerId);
        }
    }

    public ClientConnection getConnectionBySocket(WebSocket socket) {
        return activeConnections.get(socket);
    }

    public ClientConnection getConnectionByPlayerId(String playerId) {
        return playerConnections.get(playerId);
    }

    public java.util.List<String> getConnectedPlayerIds() {
        return java.util.List.copyOf(playerConnections.keySet());
    }

    public void sendToPlayer(String playerId, String jsonMessage) {
        ClientConnection conn = playerConnections.get(playerId);
        if (conn != null) {
            conn.send(jsonMessage);
        }
    }

    public void broadcast(String jsonMessage) {
        activeConnections.values().forEach(conn -> conn.send(jsonMessage));
    }

    // =========================================================================
    // ROOM ROUTING UTILITIES
    // =========================================================================

    /**
     * Synchronously links a player connection to a designated table room roster.
     */
    public void bindPlayerToTableRoom(String playerId, String targetTableId) {
        if (playerId == null || targetTableId == null) return;

        // Safety strip: Remove from any previous rooms so player is never in two spaces at once
        removePlayerFromAllRooms(playerId);

        // Normalize matching case strings to prevent splitting rosters
        String normalizedTableId = targetTableId.toUpperCase().trim();
        tableRoomRosters.computeIfAbsent(normalizedTableId, k -> ConcurrentHashMap.newKeySet()).add(playerId);

        System.out.println("[SessionManager] Bound player " + playerId + " securely to room: " + normalizedTableId);
    }

    /**
     * Unbinds a player directly (useful when a player leaves a table back to the lobby).
     */
    public void unbindPlayerFromTableRoom(String playerId, String tableId) {
        if (playerId == null || tableId == null) return;
        Set<String> roster = tableRoomRosters.get(tableId.toUpperCase().trim());
        if (roster != null) {
            roster.remove(playerId);
            if (roster.isEmpty()) {
                tableRoomRosters.remove(tableId.toUpperCase().trim());
            }
            System.out.println("[SessionManager] Unbound player " + playerId + " from room: " + tableId);
        }
    }

    /**
     * Returns a thread-safe read-only view of player IDs inside a targeted table room.
     */
    public Set<String> getPlayersInRoom(String tableId) {
        if (tableId == null) return Collections.emptySet();
        Set<String> roster = tableRoomRosters.get(tableId.toUpperCase().trim());
        return roster != null ? roster : Collections.emptySet();
    }

    /**
     * Internal cleaner helper to scrub player occurrences completely out of the roster map.
     */
    private void removePlayerFromAllRooms(String playerId) {
        tableRoomRosters.forEach((tableId, roster) -> {
            if (roster.remove(playerId) && roster.isEmpty()) {
                tableRoomRosters.remove(tableId);
            }
        });
    }
}