package pokergame.server.network;

import org.java_websocket.WebSocket;

public class ClientConnection {
    private final String playerId;
    private final WebSocket session;

    public ClientConnection(String playerId, WebSocket session) {
        this.playerId = playerId;
        this.session = session;
    }

    public String getPlayerId() {
        return playerId;
    }

    /**
     * Sends a raw message over the socket back to this specific client.
     */
    public void send(String messageJson) {
        if (session != null && session.isOpen()) {
            session.send(messageJson);
        }
    }

    public void close() {
        if (session != null) {
            session.close();
        }
    }
}