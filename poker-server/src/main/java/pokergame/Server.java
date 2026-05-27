package pokergame;

import pokergame.dbinfrastructure.HikariDSProvider;
import pokergame.dbinfrastructure.SqlPlayerRepository;
import pokergame.domain.repository.IPlayerRepository;
import pokergame.engine.GameCommandProcessor;
import pokergame.engine.PokerGameEngine;
import pokergame.network.PokerWebSocketServer;

public class Server {
    public static void main(String[] args) {
        // 1. Initialize your data access layer
        HikariDSProvider dsProvider = new HikariDSProvider();
        IPlayerRepository repo = new SqlPlayerRepository(dsProvider);
        GameContext.setPlayerRepository(repo);
        PokerGameEngine engine = new PokerGameEngine(repo);
        // 3. Fire up your single-threaded background sequential execution command queue
        GameCommandProcessor processor = new GameCommandProcessor(engine);

        // 4. Start your network boundary on port 8080, passing the command processor down
        PokerWebSocketServer server = new PokerWebSocketServer(8080, processor);
        server.start();
    }
}
