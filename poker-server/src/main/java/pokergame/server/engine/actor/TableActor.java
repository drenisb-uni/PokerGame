package pokergame.server.engine.actor;

import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.server.bot.BotManager;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.engine.actor.messages.*;

import java.util.concurrent.*;

public class TableActor implements Runnable {
    private final BlockingQueue<ActorMessage> mailbox = new LinkedBlockingQueue<>();
    private final PokerGameEngine gameEngine;
    private final BotManager botManager;
    private final IPlayerRepository playerRepository;
    private final ScheduledExecutorService actorScheduler;
    private final ExecutorService dbWorkerPool;
    private final String tableId;
    private volatile boolean running = true;

    // CORRECTED CONSTRUCTOR
    public TableActor(String tableId, IPlayerRepository playerRepository) {
        this.tableId = tableId;
        this.playerRepository = playerRepository;

        // The actor instantiates its own engine. No external thread can touch it.
        this.gameEngine = new PokerGameEngine(playerRepository);
        this.botManager = new BotManager(this, gameEngine);
        gameEngine.setTableId(tableId);

        // Confine schedulers and async pools inside this actor's boundary
        this.actorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Actor-Timer-" + tableId);
            t.setDaemon(true);
            return t;
        });

        this.dbWorkerPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Actor-DB-Worker-" + tableId);
            t.setDaemon(true);
            return t;
        });
    }

    public PokerGameEngine getInternalEngine() {
        return this.gameEngine;
    }

    /**
     * Public gateway method. Safe to be called by multiple WebSocket threads.
     */
    public void tell(ActorMessage message) {
        mailbox.offer(message);
    }

    @Override
    public void run() {
        System.out.println("[Actor] Table Event Loop started successfully.");
        while (running) {
            try {
                // Blocks gracefully until a message arrives. Zero CPU wastage.
                ActorMessage message = mailbox.take();
                processMessage(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Actor Error] Exception encountered in sequential game loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void processMessage(ActorMessage message) {
        // All mutations to gameEngine happen strictly right here, sequentially!
        if (message instanceof PlayerActionMessage action) {
            handleGameplayAction(action);
        } else if (message instanceof DatabaseProfileLoadedMessage dbMsg) {
            // Safely seat the player now that the data is loaded without blocking the engine
            if (dbMsg.profile() != null) {
                gameEngine.getTableManager().sitRealPlayer(dbMsg.profile(), dbMsg.buyIn());

                // Fix: Provide both target criteria parameters
                String pId = dbMsg.profile().id();
                String uName = dbMsg.profile().username();

                gameEngine.getBroadcaster().sendTargetedSnapshot(pId, uName);
                gameEngine.getBroadcaster().broadcastTableSnapshot();
            }
        } else if (message instanceof PlayerDisconnectedMessage discrete) {
            gameEngine.DisconnectPlayer(discrete.playerId());
        } else if (message instanceof SystemTickMessage tick) {
            handleSystemTick(tick);
        }
    }

    private void handleGameplayAction(PlayerActionMessage action) {
        switch (action.actionType()) {
            case "FOLD" -> gameEngine.Fold(action.playerId());
            case "CALL" -> gameEngine.Call(action.playerId());
            case "RAISE" -> gameEngine.Raise(action.playerId(), action.amount());
            case "ADD_BOT" -> botManager.AddBot(1000);
            case "START_HAND" -> gameEngine.startNewHand();
            case "LEAVE_TABLE" -> gameEngine.LeaveTable(action.playerId());
            case "JOIN_TABLE" -> queueAsyncDatabaseLoad(action.playerId(), action.amount());
        }
    }

    /**
     * Safely inspects the engine's current human seat count.
     * Because TableManager state might be updated asynchronously, we read the engine safely.
     */
    public int getHumanPlayerCount() {
        // If your engine tracks bot flags on profiles, filter them out:
        return (int) this.gameEngine.getTableManager().getActivePlayers().stream()
                .filter(seat -> seat.getProfile() != null && !seat.getProfile().username().startsWith("Bot_"))
                .count();
    }

    /**
     * FIXES BOTTLENECK 1: Offloads DB queries to a background pool.
     * Once loaded, it drops a message back into our own mailbox.
     */
    private void queueAsyncDatabaseLoad(String playerId, int buyIn) {
        if (playerId.startsWith("Bot_")) {
            gameEngine.JoinTable(playerId, buyIn);
            return;
        }

        dbWorkerPool.submit(() -> {
            try {
                PlayerProfileDTO profile = playerRepository.findProfileById(playerId);
                // Return result safely back into the single-threaded actor loop
                this.tell(new DatabaseProfileLoadedMessage(playerId, profile, buyIn));
            } catch (Exception e) {
                System.err.println("[Actor DB Worker] Failed to load user: " + playerId);
            }
        });
    }

    /**
     * Timers just send a message to the inbox instead of directly altering state.
     */
    public void scheduleTimerAction(String eventType, long delayMs) {
        actorScheduler.schedule(() -> {
            this.tell(new SystemTickMessage(eventType));
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void handleSystemTick(SystemTickMessage tick) {
        if ("AUTO_START_HAND".equals(tick.eventType())) {
            gameEngine.startNewHand();
        } else if ("PLAYER_TIMEOUT".equals(tick.eventType())) {
            String activeUser = gameEngine.getTableManager().getCurrentPlayer().getUsername();
            gameEngine.Fold(activeUser);
        }
    }

    public void shutdown() {
        this.running = false;

        if (this.actorScheduler != null) {
            this.actorScheduler.shutdownNow();
        }

        if (this.dbWorkerPool != null) {
            this.dbWorkerPool.shutdown();
        }
    }

    public String getTableId() {
        return tableId;
    }
}