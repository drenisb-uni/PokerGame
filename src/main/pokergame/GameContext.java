package pokergame;

import pokergame.domain.repository.IPlayerRepository;
import pokergame.engine.PokerGameEngine;

public class GameContext {
    private static IPlayerRepository playerRepository;
    private static PokerGameEngine pokerGameEngine;

    public static void setPlayerRepository(IPlayerRepository repo) {
        playerRepository = repo;
    }

    public static IPlayerRepository getPlayerRepository() {
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
