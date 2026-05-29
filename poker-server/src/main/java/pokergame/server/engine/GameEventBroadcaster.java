package pokergame.server.engine;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.server.domain.model.TableSeat;
import pokergame.domain.rules.HandResult;
import java.util.ArrayList;
import java.util.List;

public class GameEventBroadcaster {
    private final List<IGameEventListener> observers = new ArrayList<>();
    private int actionSequenceCounter = 0;

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
}