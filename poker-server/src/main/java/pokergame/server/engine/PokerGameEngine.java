package pokergame.server.engine;

import pokergame.domain.model.*;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.IPublicActionAPI;
import pokergame.engine.commands.PlayerCommand;
import pokergame.server.bot.BotManager;
import pokergame.server.domain.model.Deck;
import pokergame.server.domain.model.TableSeat;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.domain.rules.*;
import pokergame.server.domain.rules.HandRanker;
import pokergame.server.engine.actor.TableActor;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PokerGameEngine implements IPublicActionAPI {

    // Note: The scheduler remains static, but its executions will now lock on the specific engine instance
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static final int MAX_SEATS = 6;
    private static final int DEFAULT_BUY_IN = 1000;

    private final IPlayerRepository playerRepository;
    private final TableManager tableManager = new TableManager();
    private final BettingPot bettingPot = new BettingPot();
    private final GameEventBroadcaster broadcaster = new GameEventBroadcaster();
    private final Deck deck = new Deck();
    private final List<Card> communityCards = new ArrayList<>();
    private GameState currentState = GameState.WAITING_FOR_PLAYERS;
    private String currentHandId;
    private boolean nextHandScheduled = false;
    private int tableBuyIn = DEFAULT_BUY_IN;
    private String tableId;

    private List<TableSeat> winners;

    public PokerGameEngine(IPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        this.broadcaster.setGameEngine(this);
    }


    // --- SYNCHRONIZED PUBLIC API BOUNDARY ---
    public synchronized void startNewHand() {
        if (tableManager.size() < 2 || isHandInProgress()) {
            System.out.println("[Engine] Cannot start new hand: Not enough players or hand already active.");
            return;
        }

        long activePlayerCount = this.tableManager.getSeats().stream()
                .filter(java.util.Objects::nonNull) // 1. Filter out empty seat elements first
                .filter(seat -> seat.getProfile() != null) // 2. Now it is completely safe to inspect profiles
                .count();

        if (activePlayerCount < 2) {
            System.out.println("[Engine Warning] Cannot start hand: At least 2 players must be seated. (Current: " + activePlayerCount + ")");
            return; // Fail-safe exit. Do not process blinds.
        }

        nextHandScheduled = false;
        currentHandId = UUID.randomUUID().toString();
        communityCards.clear();
        bettingPot.clearPot();
        deck.reset();
        deck.shuffleDeck();

        List<TableSeat> activeSeats = getActiveSeats();

        for (TableSeat seat : activeSeats) {
            seat.setFolded(false);
            seat.setRoundBet(0);
            seat.clearCards();
        }

        for (TableSeat seat : activeSeats) {
            seat.setHoleCards(deck.getNextCard());
            seat.setHoleCards(deck.getNextCard());
        }

        currentState = GameState.PRE_FLOP_BETTING;
        bettingPot.collectBlinds(tableManager);

        broadcaster.broadcastGameState(currentState);
        broadcaster.broadcastTableSnapshot();
        broadcastBlindActions();
        promptNextPlayer();
    }

    public synchronized void processIncomingCommand(PlayerCommand command) {
        command.execute(this);
    }

    @Override
    public synchronized void Fold(String actorId) {
        TableSeat actor = validateAndGetActor(actorId);
        if (actor == null) return;

        bettingPot.handleFold(actor);
        broadcaster.broadcastAction(actor, "FOLD", 0, currentState, currentHandId);
        advanceTurn();
    }

    @Override
    public synchronized void Call(String actorId) {
        TableSeat actor = validateAndGetActor(actorId);
        if (actor == null) return;

        int broadcastAmount = bettingPot.getHighestBet() - actor.getCurrentRoundBet();
        bettingPot.handleCall(actor);

        broadcaster.broadcastAction(actor, "CALL", broadcastAmount, currentState, currentHandId);
        advanceTurn();
    }

    @Override
    public synchronized void Raise(String actorId, int amount) {
        TableSeat actor = validateAndGetActor(actorId);
        if (actor == null) return;

        bettingPot.handleRaise(actor, amount, tableManager.getActivePlayerCount());

        broadcaster.broadcastAction(actor, "RAISE", amount, currentState, currentHandId);
        advanceTurn();
    }

    @Override
    public synchronized void JoinTable(String playerId, int buyIn) {
        boolean seatedSuccessfully = false;

        if (playerId != null && playerId.startsWith("Bot_")) {
            seatedSuccessfully = tableManager.sitBot(playerId, buyIn);
            System.out.println("[Engine] BOT: " + playerId + " successfully joined the table.");
        } else {
            PlayerProfileDTO actualProfile = playerRepository.findProfileById(playerId);

            if (actualProfile != null) {
                seatedSuccessfully = tableManager.sitRealPlayer(actualProfile, buyIn);
                broadcaster.broadcastTableSnapshot();
            } else {
                System.err.println("[Engine] Failed to join: No database profile found for ID " + playerId);
                return;
            }
        }

        if (seatedSuccessfully) {
            System.out.println("[Engine] " + playerId + " successfully joined the table.");
            broadcaster.broadcastTableSnapshot();

            if (tableManager.getActivePlayerCount() >= 6 && currentState == GameState.WAITING_FOR_PLAYERS) {
                startNewHand();
            }
        }
    }

    @Override
    public synchronized void LeaveTable(String playerId) {
        leavePlayer(playerId);
        broadcaster.broadcastTableSnapshot();
    }

    @Override
    public synchronized void DisconnectPlayer(String playerId) {
        System.out.println("[Engine Alert] Cleaning up disconnected player: " + playerId);
        tableManager.handleCatastrophicDisconnect(playerId);
        leavePlayer(playerId);
        broadcaster.broadcastTableSnapshot();
    }

    @Override
    public synchronized void AddBot() {
        broadcaster.broadcastTableSnapshot();
    }

    @Override
    public synchronized void RefreshSnapshot(String playerId) {
        broadcaster.sendTargetedSnapshot(playerId, "");

        if (isHandInProgress()) {
            TableSeat actor = tableManager.getCurrentPlayer();
            if (actor != null) {
                int amountToCall = bettingPot.getHighestBet() - actor.getCurrentRoundBet();
                broadcaster.broadcastTurnPrompt(actor, amountToCall);
            }
        }
    }

    @Override
    public synchronized void StartHand() {
        startNewHand();
    }

    public synchronized void configureTableBuyIn(int buyIn) {
        if (!isBetweenHands() || tableManager.size() > 0) {
            return;
        }

        this.tableBuyIn = Math.max(1, buyIn);
        bettingPot.setSmallBlindAmount(Math.max(1, this.tableBuyIn / 50));
    }

    public synchronized TableSeat sitPlayerDown(String id, int chips, int idx) {
        return tableManager.findByUsername(id).orElseGet(() -> {
            int seatIndex = idx >= 0 ? idx : findFirstOpenSeatIndex();
            if (seatIndex < 0 || seatIndex >= tableManager.size()) {
                System.err.println("[Engine] Cannot sit player " + id + ": Table is full or index invalid.");
                return null;
            }

            PlayerProfileDTO profile = loadSeatProfile(id, chips);
            if (profile == null) {
                System.err.println("[Engine] Cannot sit player " + id + ": Profile failed to load.");
                return null;
            }

            TableSeat seat = new TableSeat(profile, chips);
            if (playerRepository != null && profile.email() != null) {
                int bankrollBase = profile.totalBankroll() - chips;
                seat.trackBankrollFromBase(bankrollBase);
                saveBankroll(profile, bankrollBase);
            }

            seat.setSeatIndex(seatIndex);
            tableManager.getSeats().set(seatIndex, seat);
            broadcaster.broadcastSeatOccupied(seat, currentHandId);

            System.out.println("[Engine] Successfully seated " + id + " at index " + seatIndex);
            return seat;
        });
    }

    public synchronized TableSeat sitPlayerDown(String id) {
        return sitPlayerDown(id, tableBuyIn, -1);
    }


    // --- INTERNAL PRIVATE LOGIC (Protected by public synchronized boundaries) ---
    private void scheduleNextHand() {
        if (nextHandScheduled || tableManager.size() < 2) return;
        nextHandScheduled = true;

        // THE SCHEDULER FIX: We must lock the engine instance when the background thread fires!
        scheduler.schedule(() -> {
            synchronized (this) {
                startScheduledHandIfReady();
            }
        }, 15, TimeUnit.SECONDS);
    }

    private void startScheduledHandIfReady() {
        if (tableManager.size() >= 2 && currentState == GameState.HAND_OVER) {
            startNewHand();
            return;
        }

        nextHandScheduled = false;
        startHandIfReady();
    }

    private void advanceTurn() {
        if (tableManager.getActivePlayerCount() <= 1) {
            handleEarlyWin();
            return;
        }

        if (bettingPot.isRoundComplete(tableManager)) {
            advanceGameStage();
        } else {
            tableManager.moveToNextActivePlayer();
            promptNextPlayer();
        }
    }

    private void advanceGameStage() {
        bettingPot.resetRound(tableManager);

        switch (currentState) {
            case PRE_FLOP_BETTING -> dealCommunityStage(GameState.FLOP_BETTING, 3);
            case FLOP_BETTING -> dealCommunityStage(GameState.TURN_BETTING, 1);
            case TURN_BETTING -> dealCommunityStage(GameState.RIVER_BETTING, 1);
            case RIVER_BETTING -> evaluateShowdown();
        }
        System.out.print(currentState+"\n");
    }

    private void dealCommunityStage(GameState nextStage, int count) {
        List<Card> newCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Card c = deck.getNextCard();
            communityCards.add(c);
            newCards.add(c);
        }
        currentState = nextStage;
        broadcaster.broadcastGameState(currentState);
        broadcaster.broadcastCards(newCards);

        tableManager.setCurrentPlayerIndex(tableManager.getDealerIndex());
        tableManager.moveToNextActivePlayer();
        bettingPot.setPlayersToAct(tableManager.getActivePlayerCount());
        promptNextPlayer();
    }

    private void promptNextPlayer() {
        TableSeat actor = tableManager.getCurrentPlayer();
        int amountToCall = bettingPot.getHighestBet() - actor.getCurrentRoundBet();
        broadcaster.broadcastTurnPrompt(actor, amountToCall);
    }

    private void handleEarlyWin() {
        tableManager.getActivePlayers().stream().findFirst().ifPresent(winner -> {
            winner.addChipsOnTable(bettingPot.getPotSize());
            broadcaster.broadcastResult(List.of(winner), null, bettingPot.getPotSize());
        });
        endHand();
    }

    private void evaluateShowdown() {
        HandRanker ranker = new HandRanker();
        Card[] commCardsArray = communityCards.toArray(new Card[0]);
        List<TableSeat> activePlayers = tableManager.getActivePlayers();

        Map<TableSeat, HandResult> playerResults = new HashMap<>();
        for (TableSeat seat : activePlayers) {
            playerResults.put(seat, ranker.evaluate(seat.getHoleCards(), commCardsArray));
        }

        HandResult bestResult = playerResults.values().stream().max(HandResult::compareTo).orElseThrow();

        winners = activePlayers.stream()
                .filter(seat -> playerResults.get(seat).compareTo(bestResult) == 0).toList();

        bettingPot.awardPotToWinners(winners);
        broadcaster.broadcastResult(winners, bestResult, bettingPot.getPotSize());
        endHand();
    }

    private void endHand() {
        currentState = GameState.HAND_OVER;
        updateTrackedBankrolls();
        broadcaster.broadcastGameState(currentState);
        tableManager.rotateDealer();
        scheduleNextHand();
    }

    public synchronized void leavePlayer(String identifier) {
        TableSeat seat = tableManager.findByIdOrUsername(identifier).orElse(null);

        if (seat == null) {
            System.err.println("[Engine] Ignored leave request: Player " + identifier + " is not at the table.");
            return;
        }

        System.out.println("[Engine] Successfully found player " + identifier + " in Seat " + seat.getSeatIndex() + ". Removing...");

        if (seat.isBankrollTracked()) {
            // Handled during updateTrackedBankrolls
        }

        if (isBetweenHands()) {
            updateTrackedBankrolls();
            tableManager.removeById(identifier);
            broadcaster.broadcastTableSnapshot();
            return;
        }

        boolean wasCurrentPlayer = tableManager.getCurrentPlayer() != null &&
                tableManager.getCurrentPlayer().equals(seat);

        if (!seat.isFolded()) {
            bettingPot.handleFold(seat);
            seat.setFolded(true);
            broadcaster.broadcastAction(seat, "LEFT TABLE", 0, currentState, currentHandId);
        }

        updateTrackedBankrolls();
        tableManager.removeById(identifier);

        if (tableManager.getActivePlayerCount() <= 1) {
            handleEarlyWin();
        } else if (wasCurrentPlayer || bettingPot.isRoundComplete(tableManager)) {
            advanceTurn();
        } else {
            broadcaster.broadcastTableSnapshot();
        }
        pauseIfNoHumansLeft();
    }

    public void pauseIfNoHumansLeft() {
        boolean humanFound = false;

        for (TableSeat seat : tableManager.getSeats()) {
            if (seat != null && seat.getProfile() != null) {
                String username = seat.getProfile().username();
                if (username != null && !username.startsWith("Bot_")) {
                    humanFound = true;
                    break;
                }
            }
        }

        if (!humanFound) {
            System.out.println("[Engine] Last human left. Evicting bots and pausing game...");

            for (int i = 0; i < tableManager.getSeats().size(); i++) {
                TableSeat seat = tableManager.getSeats().get(i);
                if (seat != null && seat.getProfile() != null) {
                    if (seat.getProfile().username().startsWith("Bot_")) {
                        tableManager.getSeats().set(i, null);
                    }
                }
            }

            currentState = GameState.WAITING_FOR_PLAYERS;
            bettingPot.clearPot();
        }
    }


    // --- SAFE READ-ONLY HELPERS ---
    private boolean isBetweenHands() {
        return currentState == GameState.WAITING_FOR_PLAYERS || currentState == GameState.HAND_OVER;
    }

    private boolean isHandInProgress() {
        return currentState == GameState.PRE_FLOP_BETTING
                || currentState == GameState.FLOP_BETTING
                || currentState == GameState.TURN_BETTING
                || currentState == GameState.RIVER_BETTING;
    }

    private List<TableSeat> getActiveSeats() {
        return tableManager.getSeats().stream()
                .filter(seat -> seat != null && seat.getProfile() != null)
                .toList();
    }

    private TableSeat validateAndGetActor(String id) {
        TableSeat actor = tableManager.getCurrentPlayer();
        if (actor == null || !actor.getProfile().id().equals(id)) {
            System.err.println("[Engine Warning] Ignored out-of-turn action from: " + id);
            return null;
        }
        return actor;
    }

    public synchronized void startHandIfReady() {
        if (currentState == GameState.HAND_OVER && nextHandScheduled) {
            return;
        }

        if (tableManager.size() >= 6 && isBetweenHands()) {
            startNewHand();
        }
    }

    // Pass-through getters and remaining non-mutating methods...

    public int getPotSize() { return bettingPot.getPotSize(); }
    public int getHighestCurrentBet() { return bettingPot.getHighestBet(); }
    public TableSeat getPlayerByUsername(String name) { return tableManager.findByUsername(name).orElse(null); }
    public GameState getCurrentState() { return currentState; }
    public int getMaxSeats() { return MAX_SEATS; }
    public int getTableBuyIn() { return tableBuyIn; }
    public int getSmallBlindAmount() { return bettingPot.getSmallBlindAmount(); }
    public int getBigBlindAmount() { return bettingPot.getBigBlindAmount(); }
    public synchronized List<Card> getCommunityCards() { return List.copyOf(communityCards); }
    public void addObserver(IGameEventListener obs) { broadcaster.addObserver(obs); }
    public synchronized boolean hasPlayer(String username) {
        return tableManager.findByUsername(username).isPresent();
    }

    public synchronized boolean hasSeatAvailable() {
        return findFirstOpenSeatIndex() >= 0;
    }

    public synchronized List<String> getSeatedUsernames() {
        return tableManager.getSeats().stream()
                .filter(Objects::nonNull)
                .map(TableSeat::getUsername)
                .toList();
    }

    public synchronized List<HandParticipantDTO> getTableParticipants() {
        return getTableParticipantsForViewer(null, false);
    }

    public synchronized List<HandParticipantDTO> getTableParticipantsForViewer(String viewerUsername, boolean revealAllCards) {
        return tableManager.getSeats().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TableSeat::getSeatIndex))
                .map(seat -> new HandParticipantDTO(
                        currentHandId == null ? "WAITING_FOR_HAND" : currentHandId,
                        seat.getUsername(),
                        seat.getSeatIndex(),
                        holeCardsForSeat(seat, viewerUsername, revealAllCards),
                        seat.getChipsOnTable(),
                        seat.getChipsOnTable(),
                        0,
                        null,
                        false
                ))
                .toList();
    }

    private void broadcastBlindActions() {
        int dealerIndex = tableManager.getDealerIndex();

        int sbIndex = tableManager.getNextActivePlayerIndex(dealerIndex);
        int bbIndex = tableManager.getNextActivePlayerIndex(sbIndex);

        broadcaster.broadcastAction(tableManager.getSeatAt(sbIndex), "SMALL BLIND", bettingPot.getSmallBlindAmount(), currentState, currentHandId);
        broadcaster.broadcastAction(tableManager.getSeatAt(bbIndex), "BIG BLIND", bettingPot.getBigBlindAmount(), currentState, currentHandId);
    }

    private String holeCardsForSeat(TableSeat seat, String viewerUsername, boolean revealAllCards) {
        if (seat.getHoleCards().isEmpty()) {
            return "HIDDEN";
        }

        if (!revealAllCards && !seat.getUsername().equals(viewerUsername)) {
            return "HIDDEN";
        }

        return seat.getHoleCards().stream()
                .map(this::cardToToken)
                .reduce((left, right) -> left + "," + right)
                .orElse("HIDDEN");
    }

    private String cardToToken(Card card) {
        String value = switch (card.getValue()) {
            case 10 -> "T";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(card.getValue());
        };
        return value + card.getSuit().substring(0, 1).toLowerCase();
    }

    private PlayerProfileDTO loadSeatProfile(String username, int buyIn) {
        if (playerRepository == null) {
            return new PlayerProfileDTO(username, username, null, null, buyIn, null);
        }

        PlayerProfileDTO profile = playerRepository.findProfileByUsername(username);
        if (profile == null) {
            return new PlayerProfileDTO(username, username, null, null, buyIn, null);
        }

        if (profile.totalBankroll() < buyIn) {
            return null;
        }

        return profile;
    }

    private void updateTrackedBankrolls() {
        if (playerRepository == null) {
            return;
        }

        for (TableSeat seat : tableManager.getSeats()) {
            if (seat == null) continue;
            if (!seat.isBankrollTracked()) {
                continue;
            }

            PlayerProfileDTO profile = seat.getProfile();
            saveBankroll(profile, seat.getBankrollBase() + seat.getChipsOnTable());
        }
    }

    private void saveBankroll(PlayerProfileDTO profile, int bankroll) {
        if (profile == null || profile.id() == null) {
            return;
        }

        playerRepository.updateProfile(new PlayerProfileDTO(
                profile.id(),
                profile.username(),
                profile.email(),
                profile.passwordHash(),
                Math.max(0, bankroll),
                profile.createdAt()
        ));
    }

    private int findFirstOpenSeatIndex() {
        Set<Integer> occupiedIndexes = new HashSet<>();
        for (TableSeat seat : tableManager.getSeats()) {
            if (seat == null) {
                continue;
            }
            occupiedIndexes.add(seat.getSeatIndex());
        }

        for (int i = 0; i < MAX_SEATS; i++) {
            if (!occupiedIndexes.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    public TableManager getTableManager() {
        return tableManager;
    }

    public BettingPot getBettingPot() {
        return bettingPot;
    }

    public GameEventBroadcaster getBroadcaster() {
        return broadcaster;
    }


    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public List<TableSeat> getWinners() {
        return winners;
    }
}