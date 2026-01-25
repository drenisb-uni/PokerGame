package main.pokergame.domain.repository;

import main.pokergame.domain.dto.HandHistoryDTO;
import main.pokergame.domain.model.Deck;
import main.pokergame.domain.model.Pot;
import main.pokergame.domain.model.TableSeat;

import java.util.List;

public interface GameRepository {
    public HandHistoryDTO findHandHistoryById(String id);
    public void saveGame(HandHistoryDTO game);
}

