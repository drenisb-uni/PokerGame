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

import java.util.*;

public class PokerGameEngine implements IPublicActionAPI {
    private static final int MAX_SEATS = 6;
    private static final int DEFAULT_BUY_IN = 1000;

    private final IPlayerRepository playerRepository;
    private final TableManager tableManager = new TableManager();
    private final BettingPot bettingPot = new BettingPot();
    private final GameEventBroadcaster broadcaster = new GameEventBroadcaster();
    private final BotManager botManager = new BotManager(new GameCommandProcessor(this), this);

    private final Deck deck = new Deck();
    private final List<Card> communityCards = new ArrayList<>();
    private GameState currentState = GameState.WAITING_FOR_PLAYERS;
    private String currentHandId;
    private boolean nextHandScheduled = false;
    private int tableBuyIn = DEFAULT_BUY_IN;

    public PokerGameEngine(IPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        this.broadcaster.setGameEngine(this);
    }

    public synchronized void startNewHand() {
        if (tableManager.size() < 2 || currentState == GameState.PRE_FLOP_BETTING
                || currentState == GameState.FLOP_BETTING || currentState == GameState.TURN_BETTING
                || currentState == GameState.RIVER_BETTING) {
            return;
        }

        nextHandScheduled = false;
        currentHandId = UUID.randomUUID().toString();
        communityCards.clear();
        bettingPot.clearPot();

        tableManager.getSeats().forEach(seat -> {
            seat.setFolded(false);
            seat.setRoundBet(0);
            seat.clearCards();
        });

        deck.reset();
        deck.shuffleDeck();
        // Deal cards
        for (int i = 0; i < 2; i++) {
            for (TableSeat seat : tableManager.getSeats()) {
                seat.setHoleCards(deck.getNextCard());
            }
        }

        currentState = GameState.PRE_FLOP_BETTING;
        bettingPot.collectBlinds(tableManager);
        broadcaster.broadcastGameState(currentState);
        broadcastBlindActions();
        promptNextPlayer();
    }

    public void processIncomingCommand(PlayerCommand command) {
        command.execute(this);
    }

    // --- IPublicActionAPI Implementations ---

    @Override
    public void Fold(String actorUsername) {
        TableSeat actor = validateAndGetActor(actorUsername);
        if (actor == null) return;

        bettingPot.handleFold(actor);
        broadcaster.broadcastAction(actor, "FOLD", 0, currentState, currentHandId);
        advanceTurn();
    }

    @Override
    public void Call(String actorUsername) {
        TableSeat actor = validateAndGetActor(actorUsername);
        if (actor == null) return;

        int broadcastAmount = bettingPot.getHighestBet() - actor.getCurrentRoundBet();
        bettingPot.handleCall(actor);

        broadcaster.broadcastAction(actor, "CALL", broadcastAmount, currentState, currentHandId);
        advanceTurn();
    }

    @Override
    public void Raise(String actorUsername, int amount) {
        TableSeat actor = validateAndGetActor(actorUsername);
        if (actor == null) return;

        bettingPot.handleRaise(actor, amount, tableManager.getActivePlayerCount());

        broadcaster.broadcastAction(actor, "RAISE", amount, currentState, currentHandId);
        advanceTurn();
    }
    @Override
    public void JoinTable(String playerId, int buyIn) {
        boolean seatedSuccessfully = false;

        // ROUTE 1: Is this a bot?
        if (playerId != null && playerId.startsWith("Bot_")) {
            // Route directly to the bot seating method we just created
            seatedSuccessfully = tableManager.sitBot(playerId, buyIn);
            System.out.println("[Engine] " + playerId + " successfully joined the table.");

        } else {
            // ROUTE 2: It is a real human.
            // We must fetch their real profile from the database/memory repository!
            // (Assuming your Engine has access to a playerRepository or similar service)
            PlayerProfileDTO actualProfile = playerRepository.findProfileById(playerId);

            if (actualProfile != null) {
                // Pass the rich DTO to the real player seating method
                seatedSuccessfully = tableManager.sitRealPlayer(actualProfile, buyIn);
            } else {
                System.err.println("[Engine] Failed to join: No database profile found for ID " + playerId);
                return; // Abort, don't broadcast anything.
            }
        }

        // Only broadcast the massive table snapshot if they ACTUALLY sat down
        // (e.g., skipping the broadcast if the table was full or they were already seated)
        if (seatedSuccessfully) {
            System.out.println("[Engine] " + playerId + " successfully joined the table.");

            // Broadcast to everyone that the table composition has changed!
            broadcaster.broadcastTableSnapshot();

            // OPTIONAL: If this was the second person to join, you might want to auto-start the hand!
            // if (tableManager.getActivePlayerCount() >= 2 && currentState == GameState.WAITING_FOR_PLAYERS) {
            //     startNewHand();
            // }
        }
    }

    @Override
    public void LeaveTable(String playerId) {
        leavePlayer(playerId); // Uses your existing safe leave logic
        broadcaster.broadcastTableSnapshot();
    }

    @Override
    public void DisconnectPlayer(String playerId) {
        System.out.println("[Engine Alert] Cleaning up disconnected player: " + playerId);
        tableManager.handleCatastrophicDisconnect(playerId);
        leavePlayer(playerId);
        broadcaster.broadcastTableSnapshot();
    }

    @Override
    public void AddBot() {
        botManager.spawnAndSeatBot(tableBuyIn);
        broadcaster.broadcastTableSnapshot();
    }

    @Override
    public void RefreshSnapshot(String playerId) {
        broadcaster.sendTargetedSnapshot(playerId);
    }

    private TableSeat validateAndGetActor(String username) {
        TableSeat actor = tableManager.getCurrentPlayer();
        if (actor == null || !actor.getUsername().equals(username)) {
            System.err.println("[Engine Warning] Ignored out-of-turn action from: " + username);
            return null;
        }
        return actor;
    }

    public synchronized void leavePlayer(String username) {
        TableSeat seat = tableManager.findByUsername(username).orElse(null);
        if (seat == null) {
            return;
        }

        if (isBetweenHands()) {
            updateTrackedBankrolls();
            tableManager.removeByUsername(username);
            broadcaster.broadcastGameState(currentState);
            return;
        }

        boolean wasCurrentPlayer = tableManager.getCurrentPlayer().getUsername().equals(username);
        if (!seat.isFolded()) {
            bettingPot.handleFold(seat);
            broadcaster.broadcastAction(seat, "LEFT TABLE", 0, currentState, currentHandId);
        }

        if (tableManager.getActivePlayerCount() <= 1 || wasCurrentPlayer || bettingPot.isRoundComplete(tableManager)) {
            advanceTurn();
        }
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
        List<TableSeat> winners = activePlayers.stream()
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

    // Pass-through getters for tests
    public int getPotSize() { return bettingPot.getPotSize(); }
    public int getHighestCurrentBet() { return bettingPot.getHighestBet(); }
    public TableSeat getPlayerByUsername(String name) { return tableManager.findByUsername(name).orElse(null); }
    public GameState getCurrentState() { return currentState; }
    public int getMaxSeats() { return MAX_SEATS; }
    public int getTableBuyIn() { return tableBuyIn; }
    public int getSmallBlindAmount() { return bettingPot.getSmallBlindAmount(); }
    public int getBigBlindAmount() { return bettingPot.getBigBlindAmount(); }
    public List<Card> getCommunityCards() { return List.copyOf(communityCards); }
    public void addObserver(IGameEventListener obs) { broadcaster.addObserver(obs); }

    public synchronized void configureTableBuyIn(int buyIn) {
        if (!isBetweenHands() || tableManager.size() > 0) {
            return;
        }

        this.tableBuyIn = Math.max(1, buyIn);
        bettingPot.setSmallBlindAmount(Math.max(1, this.tableBuyIn / 50));
    }

    public synchronized void resetTableForBuyIn(int buyIn) {
        updateTrackedBankrolls();
        tableManager.clearSeats();
        communityCards.clear();
        currentHandId = null;
        currentState = GameState.WAITING_FOR_PLAYERS;
        bettingPot.resetAll();
        nextHandScheduled = false;
        this.tableBuyIn = Math.max(1, buyIn);
        bettingPot.setSmallBlindAmount(Math.max(1, this.tableBuyIn / 50));
        broadcaster.broadcastGameState(currentState);
    }

    public synchronized void clearTable() {
        updateTrackedBankrolls();
        tableManager.clearSeats();
        communityCards.clear();
        currentHandId = null;
        currentState = GameState.WAITING_FOR_PLAYERS;
        bettingPot.resetAll();
        nextHandScheduled = false;
        broadcaster.broadcastGameState(currentState);
    }

    public boolean canPlayerAffordBuyIn(String username, int buyIn) {
        if (playerRepository == null) {
            return true;
        }

        PlayerProfileDTO profile = playerRepository.findProfileByUsername(username);
        return profile == null || profile.totalBankroll() >= buyIn;
    }

    public synchronized TableSeat sitPlayerDown(String id, int chips, int idx) {
        return tableManager.findByUsername(id).orElseGet(() -> {
            int seatIndex = idx >= 0 ? idx : findFirstOpenSeatIndex();
            if (seatIndex < 0 || seatIndex >= MAX_SEATS) {
                return null;
            }

            PlayerProfileDTO profile = loadSeatProfile(id, chips);
            if (profile == null) {
                return null;
            }

            TableSeat seat = new TableSeat(profile, chips);
            if (playerRepository != null && profile.email() != null) {
                int bankrollBase = profile.totalBankroll() - chips;
                seat.trackBankrollFromBase(bankrollBase);
                saveBankroll(profile, bankrollBase);
            }
            seat.setSeatIndex(seatIndex);
            tableManager.addSeat(seat);
            broadcaster.broadcastSeatOccupied(seat, currentHandId);
            return seat;
        });
    }

    public synchronized TableSeat sitPlayerDown(String id) {
        return sitPlayerDown(id, tableBuyIn, -1);
    }

    public synchronized boolean removePlayer(String username) {
        if (!isBetweenHands()) {
            return false;
        }
        return tableManager.removeByUsername(username);
    }

    public synchronized boolean hasPlayer(String username) {
        return tableManager.findByUsername(username).isPresent();
    }

    public synchronized boolean hasSeatAvailable() {
        return findFirstOpenSeatIndex() >= 0;
    }

    public synchronized boolean isBetweenHands() {
        return currentState == GameState.WAITING_FOR_PLAYERS || currentState == GameState.HAND_OVER;
    }

    public synchronized void startHandIfReady() {
        if (currentState == GameState.HAND_OVER && nextHandScheduled) {
            return;
        }

        if (tableManager.size() >= 2 && isBetweenHands()) {
            startNewHand();
        }
    }

    public synchronized List<String> getSeatedUsernames() {
        return tableManager.getSeats().stream().map(TableSeat::getUsername).toList();
    }

    public synchronized List<HandParticipantDTO> getTableParticipants() {
        return getTableParticipantsForViewer(null, false);
    }

    public synchronized List<HandParticipantDTO> getTableParticipantsForViewer(String viewerUsername, boolean revealAllCards) {
        return tableManager.getSeats().stream()
                .sorted(Comparator.comparingInt(TableSeat::getSeatIndex))
                .map(seat -> new HandParticipantDTO(
                        currentHandId == null ? "WAITING_FOR_HAND" : currentHandId,
                        seat.getUsername(),
                        seat.getSeatIndex(),
                        holeCardsForSeat(seat, viewerUsername, revealAllCards),
                        seat.getChipsOnTable(),
                        seat.getChipsOnTable(),
                        0,
                        false
                ))
                .toList();
    }

    private void broadcastBlindActions() {
        int smallBlindIndex = (tableManager.getDealerIndex() + 1) % tableManager.size();
        int bigBlindIndex = (tableManager.getDealerIndex() + 2) % tableManager.size();

        broadcaster.broadcastAction(tableManager.getSeatAt(smallBlindIndex), "SMALL BLIND", bettingPot.getSmallBlindAmount(), currentState, currentHandId);
        broadcaster.broadcastAction(tableManager.getSeatAt(bigBlindIndex), "BIG BLIND", bettingPot.getBigBlindAmount(), currentState, currentHandId);
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

    private void scheduleNextHand() {
        if (nextHandScheduled || tableManager.size() < 2) {
            return;
        }

        nextHandScheduled = true;
        Thread nextHandThread = new Thread(() -> {
            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            startScheduledHandIfReady();
        }, "Next-Hand-Starter");
        nextHandThread.setDaemon(true);
        nextHandThread.start();
    }

    private synchronized void startScheduledHandIfReady() {
        if (tableManager.size() >= 2 && currentState == GameState.HAND_OVER) {
            startNewHand();
            return;
        }

        nextHandScheduled = false;
        startHandIfReady();
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
}
