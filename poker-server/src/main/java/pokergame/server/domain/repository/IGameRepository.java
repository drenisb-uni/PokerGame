package pokergame.server.domain.repository;

import pokergame.domain.dto.*;

import java.util.List;

public interface IGameRepository {

    HandHistoryDTO findHandHistoryById(String id);
    void saveHandHistory(HandHistoryDTO handHistory);

    HandActionDTO findHandActionById(String id);
    void saveHandAction(HandActionDTO handAction);

    HandParticipantDTO findHandParticipantById(String id);
    void saveHandParticipant(HandParticipantDTO handParticipant);

    PlayerProfileDTO findPlayerProfileById(String id);
    void savePlayerProfile(PlayerProfileDTO playerProfile);

    PokerTableDTO findPokerTableById(String id);
    void savePokerTable(PokerTableDTO pokerTable);

    int findOrCreatePokerTable(String name, String hosterId);
    List<PlayerHandResultDTO> findRecentHandsForPlayer(String playerId, int limit);
}
