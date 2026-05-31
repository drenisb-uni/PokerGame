package pokergame.client.view;

import javafx.application.Application;
import javafx.stage.Stage;
import pokergame.GameContext;
import pokergame.client.network.WebSocketClientAPI;
import pokergame.client.view.controllers.GameController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;

public class App extends Application {

    private static App instance;
    private WebSocketClientAPI gameClientAPI;

    public static App getInstance() {
        return instance;
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        instance = this;

        System.out.println("[Client Lifecycle] Booting Poker Client Modules...");

        // 1. Core initialization: Bind your SceneManager to the primary window stage
        // This allows SceneManager.switchScene() to work across your controllers!
        SceneManager.setPrimaryStage(primaryStage);

        // 2. Launch the user directly into the Authentication Workflow
        // Switch this string to match your exact file name (e.g., "Login.fxml" or "Signup.fxml")
        SceneManager.switchScene("Login.fxml");

        primaryStage.setTitle("Poker");
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    /**
     * Call this method after a successful login when the player enters the lobby or a game table.
     * This moves the heavy network I/O out of the app startup lifecycle.
     */
    public void connectToGameServer(String username) {
        try {
            System.out.println("[Network Initialization] Connecting to Game Loop WebSocket for: " + username);

            // Notice the port is 8081! (8080 is reserved for your Javalin HTTP server)
            String serverUri = "ws://localhost:8081?user=" + username;

            WebSocket.Listener clientNetworkListener = new WebSocket.Listener() {
                @Override
                public java.util.concurrent.CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
                    System.out.println("[" + username + " Game Packet Received]: " + data);

                    // TODO: Route your raw incoming WebSocket JSON string
                    // back to your UI event listeners here!

                    return WebSocket.Listener.super.onText(ws, data, last).toCompletableFuture();
                }
            };

            // Establish the low-level connection asynchronously
            WebSocket webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(serverUri), clientNetworkListener)
                    .join();

            // Cache the API instance globally for your UI components
            this.gameClientAPI = new WebSocketClientAPI(webSocket);
            System.out.println("[Network Initialization] Game Loop WebSocket connected successfully.");

        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to connect to the game server WebSocket: " + e.getMessage());
        }
    }

    public WebSocketClientAPI getGameClientAPI() {
        return gameClientAPI;
    }
}