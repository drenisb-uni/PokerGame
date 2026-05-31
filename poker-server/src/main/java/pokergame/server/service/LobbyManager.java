package pokergame.server.service;

import pokergame.server.bot.BotManager;
import pokergame.server.dbinfrastructure.HikariDSProvider;
import pokergame.server.dbinfrastructure.SqlPlayerRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.GameCommandProcessor;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.network.NetworkEventAdapter;
import pokergame.server.network.PokerWebSocketServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyManager {

    // Maps a unique Table ID to its specific Command Processor
    private final Map<String, GameCommandProcessor> activeProcessors = new ConcurrentHashMap<>();

    // We need a reference to the WebSocket server so we can attach the Network Bridges
    private PokerWebSocketServer webSocketServer;

    public void setWebSocketServer(PokerWebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    /**
     * Called when a host wants to start a brand new table.
     * Returns the unique invite code/ID for the table.
     */
    public String createNewTable() {
        // 1. Generate a unique 6-character room code (or use a full UUID)
        String tableId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        HikariDSProvider dsProvider = new HikariDSProvider();
        IPlayerRepository playerRepository = new SqlPlayerRepository(dsProvider);

        PokerGameEngine gameEngine = new PokerGameEngine(playerRepository);
        GameCommandProcessor commandProcessor = new GameCommandProcessor(gameEngine);
        BotManager botManager = new BotManager(commandProcessor, gameEngine);

        // 3. Wire this specific engine to the internet
        NetworkEventAdapter networkBridge = new NetworkEventAdapter(webSocketServer);
        gameEngine.getBroadcaster().setGameEngine(gameEngine);
        gameEngine.getBroadcaster().addObserver(botManager);
        gameEngine.getBroadcaster().addObserver(networkBridge);

        // 4. Save it to the registry
        activeProcessors.put(tableId, commandProcessor);

        System.out.println("[Lobby] Table created with ID: " + tableId);
        return tableId;
    }

    /**
     * Gets the command processor for a specific table so the WebSocket
     * server can route FOLD/RAISE commands to the correct game.
     */
    public GameCommandProcessor getProcessorForTable(String tableId) {
        return activeProcessors.get(tableId);
    }

    public boolean tableExists(String tableId) {
        return activeProcessors.containsKey(tableId);
    }
}