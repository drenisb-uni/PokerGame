package pokergame.dbinfrastructure;

import pokergame.domain.dto.*;
import pokergame.domain.repository.IGameRepository;

public class SqlGameRepository implements IGameRepository {
    @Override
    public HandHistoryDTO findHandHistoryById(String id) {
        return null;
    }

    @Override
    public void saveHandHistory(HandHistoryDTO handHistory) {

    }

    @Override
    public HandActionDTO findHandActionById(String id) {
        return null;
    }

    @Override
    public void saveHandAction(HandActionDTO handAction) {

    }

    @Override
    public HandParticipantDTO findHandParticipantById(String id) {
        return null;
    }

    @Override
    public void saveHandParticipant(HandParticipantDTO handParticipant) {

    }

    @Override
    public PlayerProfileDTO findPlayerProfileById(String id) {
        return null;
    }

    @Override
    public void savePlayerProfile(PlayerProfileDTO playerProfile) {

    }

    @Override
    public PokerTableDTO findPokerTableById(String id) {
        return null;
    }

    @Override
    public void savePokerTable(PokerTableDTO pokerTable) {

    }
}
