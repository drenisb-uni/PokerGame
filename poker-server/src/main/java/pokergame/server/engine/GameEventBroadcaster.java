package pokergame.server.engine;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.server.domain.model.TableSeat;
import pokergame.domain.rules.HandResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameEventBroadcaster {
    private final List<IGameEventListener> observers = new ArrayList<>();
    private int actionSequenceCounter = 0;

    // NEW: Reference to the engine to read table state when building snapshots
    private PokerGameEngine gameEngine;

    // Call this immediately after initializing the Engine
    public void setGameEngine(PokerGameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    public void addObserver(IGameEventListener listener) { observers.add(listener); }

    public void broadcastGameState(GameState state) {
        observers.forEach(o -> o.onGameStateChanged(state));
    }

    public void broadcastCards(List<Card> cards) {
        observers.forEach(o -> o.onCommunityCardsDealt(cards));
    }

    public void broadcastSeatOccupied(TableSeat seat, String handId) {
        HandParticipantDTO dto = new HandParticipantDTO(
                handId == null ? "WAITING_FOR_HAND" : handId,
                seat.getUsername(), seat.getSeatIndex(), "HIDDEN",
                seat.getChipsOnTable(), seat.getChipsOnTable(), 0, false
        );
        observers.forEach(o -> o.onNewSeatOccupied(dto));
    }

    public void broadcastAction(TableSeat actor, String type, int amt, GameState state, String handId) {
        HandActionDTO dto = new HandActionDTO(
                0, handId, actor.getUsername(), state.name(),
                actionSequenceCounter++, type, amt
        );
        observers.forEach(o -> o.onPlayerAction(dto));
    }

    public void broadcastTurnPrompt(TableSeat actor, int amountToCall) {
        observers.forEach(o -> o.onPlayerTurn(actor.getUsername(), amountToCall));
    }

    public void broadcastResult(List<TableSeat> winners, HandResult result, int potSize) {
        List<String> usernames = winners.stream().map(TableSeat::getUsername).toList();
        observers.forEach(o -> o.onHandResult(usernames, result, potSize));
    }
    /**
     * Broadcasts the table state to EVERYONE.
     * Hides hole cards unless the hand is completely over.
     */
    public void broadcastTableSnapshot() {
        if (gameEngine == null) return;

        boolean revealAllCards = gameEngine.getCurrentState() == GameState.HAND_OVER;
        Map<String, Object> snapshot = buildSnapshotPayload(null, revealAllCards);

        // Pass the generic snapshot to your network listeners
        observers.forEach(o -> o.onTableSnapshotBroadcast(snapshot));
    }

    /**
     * Sends a snapshot strictly to ONE player.
     * Crucial for letting a player see their own hidden hole cards upon reconnecting.
     */
    public void sendTargetedSnapshot(String playerId) {
        if (gameEngine == null) return;

        Map<String, Object> snapshot = buildSnapshotPayload(playerId, false);
        observers.forEach(o -> o.onTargetedTableSnapshot(playerId, snapshot));
    }

    /**
     * Helper: Constructs the data envelope representing the entire table at this exact millisecond.
     */
    private Map<String, Object> buildSnapshotPayload(String viewerUsername, boolean revealAllCards) {
        Map<String, Object> payload = new HashMap<>();

        // 1. Core Game State
        payload.put("gameState", gameEngine.getCurrentState().name());
        payload.put("communityCards", gameEngine.getCommunityCards());

        // 2. Pot & Blind Data
        BettingPot pot = gameEngine.getBettingPot();
        payload.put("potSize", pot.getPotSize());
        payload.put("smallBlind", pot.getSmallBlindAmount());
        payload.put("bigBlind", pot.getBigBlindAmount());

        // 3. Map all physical seats into safe DTOs
        List<HandParticipantDTO> seatDTOs = gameEngine.getTableManager().getSeats().stream()
                .map(seat -> mapSeatToSafeDTO(seat, viewerUsername, revealAllCards))
                .collect(Collectors.toList());

        payload.put("seats", seatDTOs);

        return payload;
    }

    /**
     * Helper: Safely converts a TableSeat into a network DTO, stripping out
     * opponent hole cards unless it's a showdown or the viewer is the seat owner.
     */
    private HandParticipantDTO mapSeatToSafeDTO(TableSeat seat, String viewerUsername, boolean revealAllCards) {
        boolean isViewer = seat.getUsername().equals(viewerUsername);
        String cardVisibility = (isViewer || revealAllCards) ? "VISIBLE" : "HIDDEN";

        return new HandParticipantDTO(
                "CURRENT_HAND",
                seat.getUsername(),
                seat.getSeatIndex(),
                cardVisibility, // Ensures clients can't hack the network to see opponent cards!
                seat.getChipsOnTable(),
                seat.getCurrentRoundBet(),
                0,
                seat.isFolded()
        );
    }
}