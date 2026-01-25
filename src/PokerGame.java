import main.pokergame.dbinfrastructure.DataSource;
import main.pokergame.dbinfrastructure.InMemoryPlayerRepository;
import main.pokergame.dbinfrastructure.SqlPlayerRepository;
import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;
import main.pokergame.domain.repository.PlayerRepository;
import main.pokergame.engine.PokerGameEngine;
import main.pokergame.view.ConsoleDebbuger;

import java.util.*;

public class PokerGame {
    static void main() {
        // db
        String dbUrl = "jdbc:mysql://localhost:3306/poker_db";
        String dbUser = "root";
        String dbPass = "11X.gjiaDB";

        DataSource ds = new DataSource(dbUrl, dbUser, dbPass);

        PlayerRepository repository = new SqlPlayerRepository(ds);

        // logic
        PokerGameEngine engine = new PokerGameEngine(repository);

        // ui
        // MainUI mainUI = new MainUI();
        ConsoleDebbuger debbuger = new ConsoleDebbuger(engine);
        engine.addObserver(debbuger);
    }
}