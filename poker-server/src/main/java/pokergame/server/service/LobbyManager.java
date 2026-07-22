package pokergame.server.service;

import pokergame.server.bot.BotManager;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.engine.actor.TableActor;
import pokergame.server.network.NetworkEventAdapter;
import pokergame.server.network.PokerWebSocketServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Architect Note: LobbyManager acts as the Actor System Supervisor for all rooms.
 * It is responsible for room lifecycle creation, actor isolation orchestration,
 * and safe decommissioning of table threads.
 */
public class LobbyManager {

    // ARCHITECTURE FIX: Registry now maps to isolated TableActors instead of old command processors
    private final Map<String, TableActor> activeActors = new ConcurrentHashMap<>();

    // ARCHITECTURE FIX: Share a single database repository across all rooms to prevent connection pool exhaustion leaks
    private final IPlayerRepository playerRepository;

    // Dedicated thread management pool for managing individual Actor loops
    private final ExecutorService actorThreadPool;

    private PokerWebSocketServer webSocketServer;

    /**
     * Dependency-injected constructor. Accepts the globally shared repository instance.
     */
    public LobbyManager(IPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        // Uses a cached thread pool that automatically reuses idle threads for rooms
        this.actorThreadPool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("TableActor-Thread-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setWebSocketServer(PokerWebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    /**
     * Spawns a fully isolated TableActor running inside its own thread boundary.
     * Guaranteed safe from cross-table locking contentions.
     */
    public String createNewTable() {
        // 1. Generate a unique 6-character room code
        String tableId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 2. Instantiate the Actor. It will create its own internal engine instance.
        TableActor tableActor = new TableActor(tableId, playerRepository);

        // 3. Extract the confined engine instance ONLY to wire up the event bridges/observers
        PokerGameEngine gameEngine = tableActor.getInternalEngine();

        BotManager botManager = new BotManager(tableActor, gameEngine);
        NetworkEventAdapter networkBridge = new NetworkEventAdapter(webSocketServer);

        gameEngine.getBroadcaster().setGameEngine(gameEngine);
        gameEngine.getBroadcaster().addObserver(botManager);
        gameEngine.getBroadcaster().addObserver(networkBridge);

        // 4. Register the live Actor reference to your active session registry
        activeActors.put(tableId, tableActor);

        // 5. Submit the actor's runnable loop straight to your background thread execution pool
        actorThreadPool.submit(tableActor);

        System.out.println("[Lobby Supervisor] Table Actor completely encapsulated and running for Room: " + tableId);
        return tableId;
    }

    /**
     * Retrives the running Table Actor instance to safely receive messages.
     */
    public TableActor getActorForTable(String tableId) {
        if (tableId == null) return null;
        return activeActors.get(tableId);
    }

    public boolean tableExists(String tableId) {
        if (tableId == null) return false;
        return activeActors.containsKey(tableId);
    }

    /**
     * Lifecycle management: Gracefully tears down a room's actor thread once all players have left.
     */
    public void destroyTable(String tableId) {
        TableActor actor = activeActors.remove(tableId);
        if (actor != null) {
            System.out.println("[Lobby Supervisor] Table " + tableId + " is idle. Initiating structural cleanup...");

            PokerGameEngine engine = actor.getInternalEngine();
            engine.pauseIfNoHumansLeft();

            engine.getBroadcaster().getObservers().stream()
                    .filter(observer -> observer instanceof BotManager)
                    .map(observer -> (BotManager) observer)
                    .forEach(BotManager::shutdown);

            actor.shutdown();

            System.out.println("[Lobby Supervisor] Table Actor and Bot Thread Pools for Room " + tableId + " cleanly terminated.");
        }
    }
}