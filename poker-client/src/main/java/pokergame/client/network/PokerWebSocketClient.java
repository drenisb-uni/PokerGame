package pokergame.client.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import pokergame.GameContext;
import pokergame.client.utils.EventBus;
import pokergame.client.view.SceneManager;
import pokergame.domain.dto.GameMessageDTO;

import java.net.URI;
import java.net.URISyntaxException;

public class PokerWebSocketClient extends WebSocketClient {

    // 1. The Singleton Instance
    private static PokerWebSocketClient instance;
    private static String activeUri;

    // 2. The embedded Jackson Mapper (re-using our JavaTime fix!)
    private final ObjectMapper mapper;

    // Private constructor (forces the use of our connect() method)
    private PokerWebSocketClient(URI serverUri) {
        super(serverUri);
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Initializes the connection and completely blocks until the server says "Yes" or "No".
     * This ensures your LobbyController knows exactly when to switch scenes safely.
     */
    public static boolean connect(String uriString) {
        try {
            if (instance != null && instance.isOpen()) {
                if (uriString.equals(activeUri)) {
                    return true;
                }
                instance.closeBlocking();
            }

            instance = new PokerWebSocketClient(new URI(uriString));
            activeUri = uriString;

            // connectBlocking() waits for the full TCP/WS handshake to finish.
            // It returns true if successful, false if the server rejected us.
            return instance.connectBlocking();

        } catch (URISyntaxException | InterruptedException e) {
            System.err.println("[WebSocket] Connection interrupted: " + e.getMessage());
            return false;
        }
    }

    public static PokerWebSocketClient getInstance() {
        return instance;
    }

    // ==========================================
    // CORE WEBSOCKET LIFECYCLE CALLBACKS
    // ==========================================

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[WebSocket] ✅ Handshake complete! Status: " + handshakedata.getHttpStatus());
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[CLIENT NETWORK TRACER] Server says: " + message);

        try {
            // 1. PARSE ON THE NETWORK THREAD: Avoid blocking the JavaFX thread with JSON parsing
            GameMessageDTO envelope = mapper.readValue(message, GameMessageDTO.class);

            String actionType = envelope.type();
            Object rawPayload = envelope.payload();

            // 2. BROADCAST TO GAMEPLAY LISTENERS: Hand off to the EventBus for controllers to process
            EventBus.publish(envelope);

            if ("TABLE_CREATED".equals(actionType) || "TABLE_JOINED".equals(actionType)) {

                JsonNode payloadNode = mapper.valueToTree(rawPayload);

                if (payloadNode != null && payloadNode.has("tableId")) {
                    String inviteCode = payloadNode.get("tableId").asText();

                    GameContext.setCurrentTableId(inviteCode);

                    Platform.runLater(() -> {
                        System.out.println("[WebSocket] Room confirmed. Swapping UI to GameTable.fxml");
                        SceneManager.switchScene("GameTable.fxml");
                    });
                }
            }

        } catch (Exception e) {
            System.err.println("[WebSocket] ❌ Failed to process incoming server message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[WebSocket] 🔌 Disconnected. Code: " + code + " Reason: " + reason);

        Platform.runLater(() -> {
            // TODO: If the game is running, throw up a "Connection Lost" alert
            // and kick the player back to the Lobby UI.
        });
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[WebSocket] ❌ Fatal Error:");
        ex.printStackTrace();
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Accepts a Java Object (DTO), turns it into JSON, and sends it to the server.
     */
    public void sendPayload(Object payload) {
        if (!this.isOpen()) {
            System.err.println("[WebSocket] Cannot send payload. Socket is closed.");
            return;
        }

        try {
            String json = mapper.writeValueAsString(payload);
            this.send(json);
            System.out.println("[WebSocket] 📤 Sent: " + json);
        } catch (Exception e) {
            System.err.println("[WebSocket] Failed to serialize outgoing payload.");
            e.printStackTrace();
        }
    }
}
