package pokergame;

import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.repository.IPlayerRepository;
import pokergame.engine.PokerGameEngine;

public class GameContext {
    private static IPlayerRepository playerRepository;
    private static PokerGameEngine pokerGameEngine;
    private static PlayerProfileDTO playerProfile;

    public static void setPlayerRepository(IPlayerRepository repo) {
        playerRepository = repo;
    }

    public static IPlayerRepository getPlayerRepository() {
        if (playerRepository == null) {
            throw new IllegalStateException("Repository not initialized!");
        }
        return playerRepository;
    }

    public static void setPlayerProfile(PlayerProfileDTO playerProfile) {
        GameContext.playerProfile = playerProfile;
    }

    public static PlayerProfileDTO getPlayerProfile() {
        return playerProfile;
    }

    public static PokerGameEngine getPokerGameEngine() {
        if (pokerGameEngine == null) {
            pokerGameEngine = new PokerGameEngine(playerRepository);
        }
        return pokerGameEngine;
    }
}
