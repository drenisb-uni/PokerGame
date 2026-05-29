package pokergame.client.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import pokergame.engine.IPublicActionAPI;

public class WebSocketClientAPI implements IPublicActionAPI {

    private final WebSocket webSocket;
    private final ObjectMapper objectMapper;

    /**
     * @param webSocket An already connected and open java.net.http.WebSocket session
     */
    public WebSocketClientAPI(WebSocket webSocket) {
        this.webSocket = webSocket;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void Fold(String username) {
        sendAction("FOLD", 0);
    }

    @Override
    public void Call(String username) {
        sendAction("CALL", 0);
    }

    @Override
    public void Raise(String username, int amount) {
        sendAction("RAISE", amount);
    }

    /**
     * Centralized serializer that converts the actions into a standard JSON text string
     * and transmits them asynchronously over the WebSocket wire.
     */
    private void sendAction(String actionType, int amount) {
        try {
            // 1. Package data into our outbound DTO structure
            OutboundActionPayload payload = new OutboundActionPayload(actionType, amount);

            // 2. Serialize to raw JSON string -> {"action":"RAISE","amount":100}
            String jsonMessage = objectMapper.writeValueAsString(payload);

            // 3. Fire it over the network asynchronously
            if (webSocket != null) {
                CompletableFuture<WebSocket> sendFuture = webSocket.sendText(jsonMessage, true);

                // Optional diagnostic logging
                sendFuture.exceptionally(ex -> {
                    System.err.println("Failed to transmit network packet: " + ex.getMessage());
                    return null;
                });
            } else {
                System.err.println("Cannot send action! WebSocket connection is null.");
            }

        } catch (Exception e) {
            System.err.println("Serialization error within Client API layer: " + e.getMessage());
        }
    }
}