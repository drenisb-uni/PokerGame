package pokergame.domain.repository;

import pokergame.domain.dto.HandHistoryDTO;

public interface IGameRepository {
    public HandHistoryDTO findHandHistoryById(String id);
    public void saveGame(HandHistoryDTO game);
}

