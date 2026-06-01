package pokergame.server.network;

import pokergame.domain.dto.GameMessageDTO;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;

import java.util.List;
import java.util.Map;

/**
 * Listens to the core PokerEngine and translates in-game events
 * into network payloads to be broadcasted over WebSockets.
 */
public class NetworkEventAdapter implements IGameEventListener {

    private final PokerWebSocketServer webSocketServer;

    public NetworkEventAdapter(PokerWebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    @Override
    public void onTableSnapshotBroadcast(String tableID, Map<String, Object> snapshotPayload) {
        // 1. Inject the tableID into the payload so the client-side guard can verify it
        snapshotPayload.put("tableId", tableID);

        // 2. Package the message
        GameMessageDTO message = new GameMessageDTO("TABLE_SNAPSHOT", snapshotPayload);

        // 3. route ONLY to this specific table! (We will write this method next)
        webSocketServer.broadcastToTable(tableID, message);
    }

    @Override
    public void onTargetedTableSnapshot(String playerId, Map<String, Object> snapshotPayload) {
        GameMessageDTO message = new GameMessageDTO("TARGETED_SNAPSHOT", snapshotPayload);
        webSocketServer.sendMessageToPlayer(playerId, message);
    }

    @Override
    public void onGameStateChanged(GameState state) {
        GameMessageDTO message = new GameMessageDTO("GAME_STATE_CHANGED", Map.of("state", state.name()));
        webSocketServer.broadcastMessage(message);
    }

    @Override
    public void onPlayerAction(HandActionDTO action) {
        GameMessageDTO message = new GameMessageDTO("PLAYER_ACTION", action);
        webSocketServer.broadcastMessage(message);
    }

    @Override
    public void onPlayerTurn(String username, int amountToCall) {
        GameMessageDTO message = new GameMessageDTO("PLAYER_TURN",
                Map.of("username", username, "amountToCall", amountToCall));
        webSocketServer.broadcastMessage(message);
    }

    @Override
    public void onCommunityCardsDealt(List<Card> cards) {
        GameMessageDTO message = new GameMessageDTO("COMMUNITY_CARDS", Map.of("cards", cards));
        webSocketServer.broadcastMessage(message);
    }

    @Override
    public void onNewSeatOccupied(HandParticipantDTO participant) {
        GameMessageDTO message = new GameMessageDTO("SEAT_OCCUPIED", participant);
        webSocketServer.broadcastMessage(message);
    }

    @Override
    public void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize) {
        GameMessageDTO message = new GameMessageDTO("HAND_RESULT",
                Map.of("winners", winnerUsernames, "hand", winnerHand, "potSize", potSize));
        webSocketServer.broadcastMessage(message);
    }
}