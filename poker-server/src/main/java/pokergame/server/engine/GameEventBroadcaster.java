package pokergame.server.engine;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.server.domain.model.TableSeat;
import pokergame.domain.rules.HandResult;

import java.util.*;
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
                seat.getChipsOnTable(), seat.getChipsOnTable(), 0, null, false
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
        String currentTableId = gameEngine.getTableId();

        // 1. Loop through all physical seats to give active human players their customized view
        for (TableSeat seat : gameEngine.getTableManager().getSeats()) {
            if (seat == null || seat.getProfile() == null) continue;

            // Skip bots as they don't have networking sockets
            if (seat.getProfile().username().startsWith("Bot_")) continue;

            String pId = seat.getProfile().id();
            String uName = seat.getProfile().username();

            // Build a snapshot where ONLY this specific username gets their real cards exposed
            Map<String, Object> personalSnapshot = buildSnapshotPayload(uName, revealAllCards);
            observers.forEach(o -> o.onTargetedTableSnapshot(currentTableId, pId, personalSnapshot));
        }

        // 2. (Optional) Broadcast a fully blinded snapshot for true table spectators/observers
        Map<String, Object> spectatorSnapshot = buildSnapshotPayload(null, revealAllCards);
        observers.forEach(o -> o.onTableSnapshotBroadcast(currentTableId, spectatorSnapshot));
    }

    /**
     * Sends a snapshot strictly to ONE player.
     * Crucial for letting a player see their own hidden hole cards upon reconnecting.
     */
    public void sendTargetedSnapshot(String playerId, String username) {
        if (gameEngine == null) return;

        // Fix: Pass the username here so the privacy check matches properly
        Map<String, Object> snapshot = buildSnapshotPayload(username, false);

        String currentTableId = gameEngine.getTableId();
        observers.forEach(o -> o.onTargetedTableSnapshot(currentTableId, playerId, snapshot));
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

        // 3. Map physical seats to safe DTOs using strict layout indexing
        List<HandParticipantDTO> seatDTOs = new ArrayList<>();
        List<TableSeat> physicalSeats = gameEngine.getTableManager().getSeats();

        for (int i = 0; i < physicalSeats.size(); i++) {
            TableSeat seat = physicalSeats.get(i);

            if (seat == null) {
                // Create a DTO specifically flagged as an empty chair at this exact index
                seatDTOs.add(createEmptySeatDTO(i));
            } else {
                // Map the active player
                HandParticipantDTO activeDTO = mapSeatToSafeDTO(seat, viewerUsername, revealAllCards);
                seatDTOs.add(activeDTO);
            }
        }

        payload.put("seats", seatDTOs);

        return payload;
    }

    private HandParticipantDTO createEmptySeatDTO(int index) {
        return new HandParticipantDTO(
                null,
                null,
                index,
                null,
                0,
                0,
                0,
                null,
                false
        );
    }

    /**
     * Helper: Safely converts a TableSeat into a network DTO, stripping out
     * opponent hole cards unless it's a showdown or the viewer is the seat owner.
     */
    private HandParticipantDTO mapSeatToSafeDTO(TableSeat seat, String viewerUsername, boolean revealAllCards) {
        boolean isViewer = seat.getUsername().equals(viewerUsername);

        // FIX 1: Map raw Card objects to clean asset name tokens (e.g., "14-S,10-H") instead of raw memory .toString()
        String cardVisibility;
        if (isViewer || revealAllCards) {
            if (seat.getHoleCards() == null || seat.getHoleCards().isEmpty()) {
                cardVisibility = "[]";
            } else {
                List<String> cardTokens = new ArrayList<>();
                for (pokergame.domain.model.Card card : seat.getHoleCards()) {
                    if (card != null && card.getSuit() != null) {
                        // Pulls the first character of the suit (e.g., "Spades" -> "S")
                        String suitLetter = card.getSuit().trim().toUpperCase().substring(0, 1);
                        cardTokens.add(card.getValue() + "-" + suitLetter);
                    }
                }
                cardVisibility = String.join(",", cardTokens);
            }
        } else {
            cardVisibility = "HIDDEN";
        }

        // Determine winner status safely (defaulting to false if engine does not explicitly track it mid-hand)
        boolean isWinner = false;
        if (gameEngine.getWinners() != null) {
            isWinner = gameEngine.getWinners().contains(seat.getUsername());
        }

        return new HandParticipantDTO(
                "CURRENT_HAND",
                seat.getUsername(),
                seat.getSeatIndex(),
                cardVisibility,
                seat.getChipsOnTable(),
                seat.getCurrentRoundBet(),
                0,
                null,
                isWinner // FIX 2: Corrected data position mapping
        );
    }

    public List<IGameEventListener> getObservers() {
        return observers;
    }
}