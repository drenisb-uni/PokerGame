package pokergame.domain.repository;

import pokergame.domain.dto.HandHistoryDTO;
import pokergame.domain.model.Deck;
import pokergame.domain.model.Pot;
import pokergame.domain.model.TableSeat;

import java.util.List;

public interface GameRepository {
    public HandHistoryDTO findHandHistoryById(String id);
    public void saveGame(HandHistoryDTO game);
}

