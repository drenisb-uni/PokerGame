package pokergame.engine;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.rules.HandResult;

import java.util.List;
import java.util.Map;

// Inside poker-common module
public interface IGameEventListener {
    // Uses standard Strings and common enums
    void onGameStateChanged(GameState state);
    void onCommunityCardsDealt(List<Card> cards);
    void onNewSeatOccupied(HandParticipantDTO participant);
    void onPlayerTurn(String username, int amountToCall);
    void onPlayerAction(HandActionDTO action);
    void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize);
    void onTableSnapshotBroadcast(Map<String, Object> snapshotPayload);
    void onTargetedTableSnapshot(String playerId, Map<String, Object> snapshotPayload);
}
