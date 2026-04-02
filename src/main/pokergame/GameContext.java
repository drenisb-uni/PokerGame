package pokergame;

import javafx.stage.Stage;
import pokergame.dbinfrastructure.SqlPlayerRepository;
import pokergame.domain.repository.PlayerRepository;
import pokergame.engine.PokerGameEngine;

public class GameContext {
    private static PlayerRepository playerRepository;
    private static PokerGameEngine pokerGameEngine;

    public static void setPlayerRepository(PlayerRepository repo) {
        playerRepository = repo;
    }

    public static PlayerRepository getPlayerRepository() {
        if (playerRepository == null) {
            throw new IllegalStateException("Repository not initialized!");
        }
        return playerRepository;
    }

    public static PokerGameEngine getPokerGameEngine() {
        if (pokerGameEngine == null) {
            throw new IllegalStateException("Engine not initialized!");
        }
        return pokerGameEngine;
    }
}
