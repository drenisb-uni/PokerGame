package pokergame.server.network;

import org.java_websocket.WebSocket;

import java.nio.channels.DatagramChannel;

public class ClientConnection {
    private final String playerId;
    private final WebSocket session;
    private String currentTableId;

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

    public WebSocket getSocket() {
        return session;
    }

    public String getCurrentTableId() { return currentTableId; }
    public void setCurrentTableId(String currentTableId) { this.currentTableId = currentTableId; }
}