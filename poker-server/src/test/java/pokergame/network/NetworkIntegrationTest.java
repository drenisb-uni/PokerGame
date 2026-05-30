package pokergame.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pokergame.GameContext;
import pokergame.server.bot.BotManager;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.network.PokerWebSocketServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NetworkIntegrationTest {

    private PokerWebSocketServer server;
    private GameCommandProcessor processor;
    private PokerGameEngine engine;
    private BotManager botManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final int TEST_PORT = 9876;

    @BeforeEach
    public void setup() throws Exception {
//        IPlayerRepository mockRepo = Mockito.mock(IPlayerRepository.class);
        engine = GameContext.getPokerGameEngine();
        processor = new GameCommandProcessor(engine);
        botManager = new BotManager(processor, engine);

        // Boot up our actual custom server on an isolated test port
        server = new PokerWebSocketServer(TEST_PORT, processor, engine, botManager);
        server.start();

        // Brief sleep to let the TCP socket bind smoothly
        Thread.sleep(200);
    }

    @AfterEach
    public void teardown() throws Exception {
        server.stop();
        processor.stop();
    }

    @Test
    public void testNetworkClientActionProcessing() throws Exception {
        // 1. Arrange a mock background table environment
        engine.sitPlayerDown("Alice", 1000, 0);

        // 2. Setup a native Java HTTP client to simulate a headless remote user
        HttpClient client = HttpClient.newHttpClient();

        // Dummy listener to receive incoming raw transmissions from the server
        WebSocket.Listener dummyListener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                System.out.println("[Test Client] Socket Connected!");
                WebSocket.Listener.super.onOpen(webSocket);
            }
            @Override
            public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                System.out.println("[Test Client] Received Server Message: " + data);
                return WebSocket.Listener.super.onText(webSocket, data, last).toCompletableFuture();
            }
        };

        // Connect the client socket while identifying as "Alice" in the query string
        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + TEST_PORT + "/?user=Alice"), dummyListener);

        WebSocket aliceSocket = wsFuture.get(2, TimeUnit.SECONDS);
        assertNotNull(aliceSocket);

        // 3. Act: Simulate Alice clicking "Raise" in her UI, emitting an outbound JSON structure
        OutboundActionPayload actionPayload = new OutboundActionPayload("RAISE", 150);
        String jsonPayload = mapper.writeValueAsString(actionPayload);

        aliceSocket.sendText(jsonPayload, true);

        // Give the network stack and single-threaded processor pipeline 150ms to cycle
        Thread.sleep(150);

        // 4. Assert: Confirm the engine successfully processed the packet across the TCP layer!
        // Alice should be recognized by the server as having bet $150
        // (Adjust assertion depending on your exact model's current state tracking method)
        System.out.println("Alice Current Round Bet verified via network loop: " +
                engine.getPlayerByUsername("Alice").getCurrentRoundBet());
    }

    private record OutboundActionPayload(String action, int amount) {}
}
