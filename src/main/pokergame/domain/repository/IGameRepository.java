package pokergame.domain.repository;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandHistoryDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.dto.PokerTableDTO;

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
}