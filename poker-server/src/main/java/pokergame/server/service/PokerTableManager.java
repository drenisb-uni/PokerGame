package pokergame.server.service;

import io.javalin.websocket.WsContext;
import pokergame.domain.dto.GameMessageDTO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PokerTableManager {

    // Maps a username to their active WebSocket connection
    private final Map<String, WsContext> players = new ConcurrentHashMap<>();

    // --- Player Management ---

    public void addPlayer(String username, WsContext ctx) {
        players.put(username, ctx);
        System.out.println("[PokerTable] " + username + " sat down. Total players: " + players.size());

        // Announce to everyone else that a new player joined
        broadcast(new GameMessageDTO("PLAYER_JOINED", username + " has joined the table."));
    }

    public void removePlayer(String username) {
        if (username != null && players.remove(username) != null) {
            System.out.println("[PokerTable] " + username + " left the table.");
            broadcast(new GameMessageDTO("PLAYER_LEFT", username + " has left the table."));
        }
    }

    // --- Networking (The Dealer's Voice) ---

    /**
     * Sends a message to EVERYONE at the table.
     */
    public void broadcast(GameMessageDTO message) {
        players.values().stream()
                .filter(ctx -> ctx.session.isOpen())
                .forEach(ctx -> ctx.send(message));
    }

    /**
     * Sends a private message to a SINGLE player (e.g., dealing their hidden hole cards).
     */
    public void sendToPlayer(String username, GameMessageDTO message) {
        WsContext ctx = players.get(username);
        if (ctx != null && ctx.session.isOpen()) {
            ctx.send(message);
        }
    }
}