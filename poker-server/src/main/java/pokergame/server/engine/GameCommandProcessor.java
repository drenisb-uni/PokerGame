package pokergame.server.engine;

import pokergame.engine.IPublicActionAPI;
import pokergame.engine.commands.PlayerCommand;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameCommandProcessor {
    private final PokerGameEngine gameEngine;
    private final GameEventBroadcaster gameEventBroadcaster;
    private final ConcurrentLinkedQueue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    public GameCommandProcessor(PokerGameEngine gameEngine) {
        this.gameEngine = gameEngine;
        this.gameEventBroadcaster = gameEngine.getBroadcaster();
        this.startProcessingLoop();
    }

    // Network layer calls this when a WebSocket message arrives
    public void queueCommand(PlayerCommand command) {
        commandQueue.add(command);
    }

    private void startProcessingLoop() {
        Thread processorThread = new Thread(() -> {
            while (running) {
                try {
                    PlayerCommand command = commandQueue.poll();

                    // 1. GUARD: If the queue is empty, wait 10ms and try again
                    if (command == null) {
                        Thread.sleep(10);
                        continue;
                    }

                    System.out.println("Processing command from: " + command.getPlayerId());
                    command.execute(gameEngine);

                } catch (Exception e) {
                    System.err.println("[FATAL] Processor crashed!");
                    e.printStackTrace();
                }
            }
        }, "Game-Command-Processor-Thread");

        processorThread.setDaemon(true);
        processorThread.start();
    }

    public void stop() {
        this.running = false;
    }
}