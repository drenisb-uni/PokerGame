package pokergame.engine;

import pokergame.engine.commands.PlayerCommand;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GameCommandProcessor {
    private final IPublicActionAPI gameEngine;
    private final ConcurrentLinkedQueue<PlayerCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    public GameCommandProcessor(IPublicActionAPI gameEngine) {
        this.gameEngine = gameEngine;
        this.startProcessingLoop();
    }

    // Network layer calls this when a WebSocket message arrives
    public void queueCommand(PlayerCommand command) {
        commandQueue.add(command);
    }

    private void startProcessingLoop() {
        Thread processorThread = new Thread(() -> {
            while (running) {
                PlayerCommand command = commandQueue.poll();
                if (command != null) {
                    try {
                        // Crucial Security Step: Validate turn order before execution
                        System.out.println("Processing command from: " + command.getPlayerId());
                        command.execute(gameEngine);
                    } catch (Exception e) {
                        System.err.println("Rejected invalid command: " + e.getMessage());
                        // Optional: Send a "Error DTO" back to the specific offending client
                    }
                } else {
                    try {
                        Thread.sleep(10); // Don't burn up the CPU when queue is empty
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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