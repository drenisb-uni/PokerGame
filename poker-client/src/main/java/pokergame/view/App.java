package pokergame.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import pokergame.GameContext;
import pokergame.network.WebSocketClientAPI;
import pokergame.view.controllers.GameController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;

public class App extends Application {
    private static String currentUsername = "GuestPlayer";
    private WebSocketClientAPI clientAPI;

    public static void main(String[] args) {
        if (args.length > 0) {
            currentUsername = args[0]; // Capture username parameter from execution run configurations
        }
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("[Client Lifecycle] Initializing client window context for profile: " + currentUsername);

        // 1. Establish asynchronous network handshake loop to our server
        // The server extracts this query parameter to pair the socket session with this specific player
        String serverUri = "ws://localhost:8080?user=" + currentUsername;

        // Simple placeholder listener to map incoming server updates
        WebSocket.Listener clientNetworkListener = new WebSocket.Listener() {
            @Override
            public java.util.concurrent.CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
                System.out.println("[" + currentUsername + " Client Network Log] Received: " + data);
                return WebSocket.Listener.super.onText(ws, data, last);
            }
        };

        // Establish the low-level connection
        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(serverUri), clientNetworkListener)
                .join();

        // Wrap the socket inside your clean IPublicActionAPI implementation
        this.clientAPI = new WebSocketClientAPI(webSocket);

        // 2. Load JavaFX FXML Interface Layout views
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GameView.fxml"));
        Parent root = loader.load();

        // 3. Pass contextual identities down to your UI controller
        Object controller = loader.getController();
        if (controller instanceof GameController gameController) {
            gameController.setLocalPlayerContext(currentUsername, clientAPI);
        }

        primaryStage.setTitle("Poker Game Client - " + currentUsername);
        primaryStage.setScene(new Scene(root, 1024, 768));
        primaryStage.setResizable(true);
        primaryStage.show();
    }
}