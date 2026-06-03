package pokergame.server.bot;

import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.model.Card;
import pokergame.domain.rules.HandResult;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.server.domain.model.TableSeat;
import pokergame.server.engine.PokerGameEngine;
import pokergame.server.engine.actor.messages.PlayerActionMessage;
import pokergame.server.engine.actor.TableActor;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotManager implements IGameEventListener {
    public static final String WAITING_BOT_USERNAME = "Bot_0";

    private final TableActor tableActor;
    private final PokerGameEngine gameEngine;
    private final BotBrain botBrain;

    private final Set<String> botUsernames = ConcurrentHashMap.newKeySet();
    private final Map<String, BotPersonality> botPersonalities = new ConcurrentHashMap<>();
    private final Map<String, OpponentProfile> opponentProfiles = new ConcurrentHashMap<>();

    // ARCHITECTURE FIX: Safe, managed timer pool for bot thinking delays
    private final ScheduledExecutorService botTimerPool = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();
    private int botCounter = 1;

    public BotManager(TableActor tableActor, PokerGameEngine gameEngine) {
        this.tableActor = tableActor;
        this.gameEngine = gameEngine;
        this.botBrain = new BotBrain();
    }

    public void registerBot(String username) {
        botUsernames.add(username);
        botPersonalities.putIfAbsent(username, personalityForName(username));
    }

    public boolean isBot(String username) {
        return botUsernames.contains(username);
    }

    private String nextManualBotName() {
        String username;
        BotPersonality personality = randomPersonality();
        do {
            username = "Bot_" + botCounter++;
        } while (botUsernames.contains(username));
        botPersonalities.put(username, personality);
        return username;
    }

    public void AddBot(int tableBuyIn) {
        String botName = nextManualBotName();
        System.out.println("[BotManager] Spawning new bot: " + botName);
        tableActor.tell(new PlayerActionMessage(botName, "JOIN_TABLE", tableBuyIn));
    }

    @Override
    public void onPlayerTurn(String username, int amountToCall) {
        if (!botUsernames.contains(username)) return;

        TableSeat botSeat = gameEngine.getPlayerByUsername(username);
        if (botSeat == null || botSeat.isFolded()) return;

        int thinkingDelay = botBrain.calculateThinkingDelay(botSeat, amountToCall, gameEngine);

        // ARCHITECTURE FIX: No more unmanaged threads. Schedule the action securely.
        botTimerPool.schedule(() -> executeBotTurn(username, botSeat, amountToCall), thinkingDelay, TimeUnit.MILLISECONDS);
    }

    private void executeBotTurn(String username, TableSeat botSeat, int amountToCall) {
        // 1. Gather context
        BotPersonality personality = botPersonalities.getOrDefault(username, BotPersonality.BALANCED);
        OpponentProfile tableRead = getCombinedTableRead();

        // 2. Ask Brain for decision
        BotDecision decision = botBrain.calculateDecision(botSeat, amountToCall, gameEngine, personality, tableRead);

        // 3. Drop decision into the Actor Mailbox safely
        tableActor.tell(new PlayerActionMessage(username, decision.actionType(), decision.amount()));
    }

    private OpponentProfile getCombinedTableRead() {
        OpponentProfile combined = new OpponentProfile();
        for (OpponentProfile profile : opponentProfiles.values()) {
            combined.absorb(profile);
        }
        return combined;
    }

    private BotPersonality randomPersonality() {
        BotPersonality[] personalities = BotPersonality.values();
        return personalities[random.nextInt(personalities.length)];
    }

    private BotPersonality personalityForName(String username) {
        if (username.equals(WAITING_BOT_USERNAME)) return BotPersonality.BALANCED;
        if (username.startsWith("Tight_")) return BotPersonality.TIGHT;
        if (username.startsWith("Loose_")) return BotPersonality.LOOSE;
        if (username.startsWith("Aggro_")) return BotPersonality.AGGRESSIVE;
        if (username.startsWith("Tricky_")) return BotPersonality.TRICKY;
        return randomPersonality();
    }

    // --- Lifecycle and Cleanup ---
    public void shutdown() {
        botTimerPool.shutdownNow();
    }

    // --- Unchanged Event Listeners ---
    @Override
    public void onPlayerAction(HandActionDTO action) {
        if (!botUsernames.contains(action.playerId())) {
            opponentProfiles.computeIfAbsent(action.playerId(), ignored -> new OpponentProfile()).record(action.actionType());
        }
    }

    @Override
    public void onNewSeatOccupied(HandParticipantDTO participant) {
        String username = participant.playerUsername();
        if (username != null && username.startsWith("Bot_") && !isBot(username)) {
            registerBot(username);
        }
    }

    @Override
    public void onTableSnapshotBroadcast(String tableId, Map<String, Object> snapshotPayload) {
        List<?> seats = (List<?>) snapshotPayload.get("seats");
        if (seats != null) {
            for (Object seatObj : seats) {
                if (seatObj instanceof HandParticipantDTO participant) {
                    String username = participant.playerUsername();
                    if (username != null && username.startsWith("Bot_")) registerBot(username);
                }
            }
        }
    }

    @Override public void onGameStateChanged(GameState state) {}
    @Override public void onCommunityCardsDealt(List<Card> cards) {}
    @Override public void onHandResult(List<String> winnerUsernames, HandResult winnerHand, int potSize) {}
    @Override public void onTargetedTableSnapshot(String tableId, String playerId, Map<String, Object> snapshotPayload) {}
    @Override public void onCardsDealt(String tableId, Map<String, String> playerHoleCards) {}
    @Override public void onPotChanged(String tableId, int newPotTotal) {}
}