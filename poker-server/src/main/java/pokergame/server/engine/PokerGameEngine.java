package pokergame.server.engine;

import pokergame.domain.model.*;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandHistoryDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.IPublicActionAPI;
import pokergame.server.domain.model.Deck;
import pokergame.server.domain.model.TableSeat;
import pokergame.server.domain.repository.IGameRepository;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.domain.rules.*;
import pokergame.server.domain.rules.HandRanker;

import java.time.LocalDateTime;
import java.util.*;

public class PokerGameEngine implements IPublicActionAPI {
    private static final int MAX_SEATS = 6;
    private static final int DEFAULT_BUY_IN = 1000;

    private final IPlayerRepository playerRepository;
    private final IGameRepository gameRepository;
    private final TableManager tableManager = new TableManager();
    private final BettingPot bettingPot = new BettingPot();
    private final GameEventBroadcaster broadcaster = new GameEventBroadcaster();

    private final Deck deck = new Deck();
    private final List<Card> communityCards = new ArrayList<>();
    private GameState currentState = GameState.WAITING_FOR_PLAYERS;
    private String currentHandId;
    private LocalDateTime currentHandStartedAt;
    private int historyTableId = 0;
    private boolean nextHandScheduled = false;
    private int tableBuyIn = DEFAULT_BUY_IN;
    private final Map<String, Integer> handStartChipsByUsername = new HashMap<>();
    private final Set<String> currentWinnerUsernames = new HashSet<>();
    private String currentWinningHandRank = "";

    public PokerGameEngine(IPlayerRepository playerRepository) {
        this(playerRepository, null);
    }

    public PokerGameEngine(IPlayerRepository playerRepository, IGameRepository gameRepository) {
        this.playerRepository = playerRepository;
        this.gameRepository = gameRepository;
    }

    public synchronized void startNewHand() {
        if (tableManager.size() < 2 || currentState == GameState.PRE_FLOP_BETTING
                || currentState == GameState.FLOP_BETTING || currentState == GameState.TURN_BETTING
                || currentState == GameState.RIVER_BETTING) {
            return;
        }

        nextHandScheduled = false;
        currentHandId = UUID.randomUUID().toString();
        currentHandStartedAt = LocalDateTime.now();
        communityCards.clear();
        bettingPot.clearPot();
        handStartChipsByUsername.clear();
        currentWinnerUsernames.clear();
        currentWinningHandRank = "";

        tableManager.getSeats().forEach(seat -> {
            seat.setFolded(false);
            seat.setRoundBet(0);
            seat.clearCards();
            handStartChipsByUsername.put(seat.getUsername(), seat.getChipsOnTable());
        });

        deck.reset();
        deck.shuffleDeck();
        saveStartedHand();
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

    public void executePlayerAction(String username, String actionType, int amount) {
        performAction(username, actionType, amount);
    }

    @Override
    public void Fold(String actorUsername) {
        performAction(actorUsername, "FOLD", 0);
    }

    @Override
    public void Call(String actorUsername) {
        performAction(actorUsername, "CALL", 0);
    }

    @Override
    public void Raise(String actorUsername, int amount) {
        performAction(actorUsername, "RAISE", amount);
    }

    private void performAction(String username, String actionType, int amount) {
        TableSeat actor = tableManager.getCurrentPlayer();
        if (!actor.getUsername().equals(username)) return;

        int broadcastAmount = 0;
        switch (actionType.toUpperCase()) {
            case "FOLD" -> bettingPot.handleFold(actor);
            case "CALL" -> {
                broadcastAmount = bettingPot.getHighestBet() - actor.getCurrentRoundBet();
                bettingPot.handleCall(actor);
            }
            case "RAISE" -> {
                broadcastAmount = amount;
                bettingPot.handleRaise(actor, amount, tableManager.getActivePlayerCount());
            }
            default -> {
                return;
            }
        }

        HandActionDTO persistedAction = broadcaster.broadcastAction(actor, actionType, broadcastAmount, currentState, currentHandId);
        saveHandAction(persistedAction);
        advanceTurn();
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
            HandActionDTO action = broadcaster.broadcastAction(seat, "LEFT TABLE", 0, currentState, currentHandId);
            saveHandAction(action);
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
            currentWinnerUsernames.clear();
            currentWinnerUsernames.add(winner.getUsername());
            currentWinningHandRank = "FOLD";
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
        currentWinnerUsernames.clear();
        winners.forEach(winner -> currentWinnerUsernames.add(winner.getUsername()));
        currentWinningHandRank = bestResult.getType().name();
        broadcaster.broadcastResult(winners, bestResult, bettingPot.getPotSize());
        endHand();
    }

    private void endHand() {
        currentState = GameState.HAND_OVER;
        saveCompletedHand();
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
        historyTableId = 0;
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
        historyTableId = 0;
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
        historyTableId = 0;
        broadcaster.broadcastGameState(currentState);
    }

    public boolean canPlayerAffordBuyIn(String username, int buyIn) {
        if (playerRepository == null) {
            return true;
        }

        PlayerProfileDTO profile = findStoredProfile(username);
        return profile == null || profile.totalBankroll() >= buyIn;
    }

    public synchronized TableSeat sitPlayerDown(String id, int chips, int idx) {
        PlayerProfileDTO storedProfile = findStoredProfile(id);
        String username = storedProfile == null ? id : storedProfile.username();

        return tableManager.findByUsername(username).orElseGet(() -> {
            int seatIndex = idx >= 0 ? idx : findFirstOpenSeatIndex();
            if (seatIndex < 0 || seatIndex >= MAX_SEATS) {
                return null;
            }

            PlayerProfileDTO profile = loadSeatProfile(id, chips, storedProfile);
            if (profile == null) {
                return null;
            }

            TableSeat seat = new TableSeat(profile, chips);
            if (storedProfile != null) {
                seat.trackPersistence();
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

    private void saveStartedHand() {
        if (gameRepository == null || currentHandId == null || currentHandStartedAt == null) {
            return;
        }

        int tableId = ensureHistoryTable();
        if (tableId <= 0) {
            return;
        }

        gameRepository.saveHandHistory(new HandHistoryDTO(
                currentHandId,
                tableId,
                currentHandStartedAt,
                "",
                0,
                ""
        ));
    }

    private void saveCompletedHand() {
        if (gameRepository == null || currentHandId == null || currentHandStartedAt == null) {
            return;
        }

        int tableId = ensureHistoryTable();
        if (tableId <= 0) {
            return;
        }

        gameRepository.saveHandHistory(new HandHistoryDTO(
                currentHandId,
                tableId,
                currentHandStartedAt,
                communityCardsForHistory(),
                bettingPot.getPotSize(),
                currentWinningHandRank
        ));

        for (TableSeat seat : tableManager.getSeats()) {
            if (!shouldPersistSeat(seat)) {
                continue;
            }

            int startChips = handStartChipsByUsername.getOrDefault(seat.getUsername(), seat.getChipsOnTable());
            int endChips = seat.getChipsOnTable();
            gameRepository.saveHandParticipant(new HandParticipantDTO(
                    currentHandId,
                    seat.getUsername(),
                    seat.getSeatIndex(),
                    holeCardsForSeat(seat, seat.getUsername(), true),
                    startChips,
                    endChips,
                    endChips - startChips,
                    currentWinnerUsernames.contains(seat.getUsername())
            ));
        }
    }

    private void saveHandAction(HandActionDTO action) {
        if (gameRepository == null || action == null || action.handId() == null) {
            return;
        }

        TableSeat actor = tableManager.findByUsername(action.playerId()).orElse(null);
        if (!shouldPersistSeat(actor)) {
            return;
        }

        gameRepository.saveHandAction(action);
    }

    private boolean shouldPersistSeat(TableSeat seat) {
        return seat != null && seat.isPersistenceTracked();
    }

    private int ensureHistoryTable() {
        if (historyTableId > 0) {
            return historyTableId;
        }

        if (gameRepository == null) {
            return 0;
        }

        String hosterId = tableManager.getSeats().stream()
                .filter(this::shouldPersistSeat)
                .map(TableSeat::getProfile)
                .filter(Objects::nonNull)
                .map(PlayerProfileDTO::id)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
        historyTableId = gameRepository.findOrCreatePokerTable("Table $" + tableBuyIn, hosterId);
        return historyTableId;
    }

    private String communityCardsForHistory() {
        return communityCards.stream()
                .map(this::cardToToken)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private void broadcastBlindActions() {
        int smallBlindIndex = (tableManager.getDealerIndex() + 1) % tableManager.size();
        int bigBlindIndex = (tableManager.getDealerIndex() + 2) % tableManager.size();

        HandActionDTO smallBlind = broadcaster.broadcastAction(tableManager.getSeatAt(smallBlindIndex), "SMALL BLIND", bettingPot.getSmallBlindAmount(), currentState, currentHandId);
        HandActionDTO bigBlind = broadcaster.broadcastAction(tableManager.getSeatAt(bigBlindIndex), "BIG BLIND", bettingPot.getBigBlindAmount(), currentState, currentHandId);
        saveHandAction(smallBlind);
        saveHandAction(bigBlind);
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

    private PlayerProfileDTO loadSeatProfile(String username, int buyIn, PlayerProfileDTO storedProfile) {
        if (playerRepository == null) {
            return new PlayerProfileDTO(username, username, null, null, buyIn, null);
        }

        if (storedProfile == null) {
            return new PlayerProfileDTO(username, username, null, null, buyIn, null);
        }

        if (storedProfile.totalBankroll() < buyIn) {
            return null;
        }

        return storedProfile;
    }

    private PlayerProfileDTO findStoredProfile(String usernameOrId) {
        if (playerRepository == null || usernameOrId == null || usernameOrId.isBlank()) {
            return null;
        }

        PlayerProfileDTO profile = playerRepository.findProfileByUsername(usernameOrId);
        if (profile != null) {
            return profile;
        }

        return playerRepository.findProfileById(usernameOrId);
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
}
