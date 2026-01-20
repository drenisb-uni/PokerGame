package main.pokergame.domain.repository;

import main.pokergame.domain.model.Deck;
import main.pokergame.domain.model.Pot;
import main.pokergame.domain.model.TableSeat;

import java.util.List;

public interface GameRepository {

    public void saveRound(List<TableSeat> tableSeats, Deck deck, Pot pot);
    public void saveGame();
}

