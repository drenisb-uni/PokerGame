import main.pokergame.dbinfrastructure.InMemoryPlayerRepository;
import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;
import main.pokergame.domain.repository.PlayerRepository;
import main.pokergame.engine.PokerGameEngine;

import java.util.*;

public class PokerGame {
    static void main() {
        // db
        PlayerRepository repository = new InMemoryPlayerRepository();
        // logic
        PokerGameEngine engine = new PokerGameEngine(repository);
        // ui
        // MainUI mainUI = new MainUI();
    }
}