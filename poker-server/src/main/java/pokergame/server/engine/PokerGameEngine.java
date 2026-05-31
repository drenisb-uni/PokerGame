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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PokerGameEngine implements IPublicActionAPI {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
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

    public void startNewHand() {
        // 1. STATE GUARD: Ensure we have enough players and are not interrupting an active hand
        if (tableManager.size() < 2 || isHandInProgress()) {
            System.out.println("[Engine] Cannot start new hand: Not enough players or hand already active.");
            return;
        }

        // 2. RESET TABLE ENVIRONMENT
        nextHandScheduled = false;
        currentHandId = UUID.randomUUID().toString();
        communityCards.clear();
        bettingPot.clearPot();
        deck.reset();
        deck.shuffleDeck();

        // 3. FILTER ACTIVE SEATS (The Bulletproof Fix)
        // We fetch a clean list of ONLY valid, occupied seats to avoid NullPointerExceptions.
        List<TableSeat> activeSeats = getActiveSeats();

        // 4. RESET PLAYERS
        for (TableSeat seat : activeSeats) {
            seat.setFolded(false);
            seat.setRoundBet(0);
            seat.clearCards();
        }

        // 5. DEAL HOLE CARDS
        // Standard poker dealing: 1 card to every active player, then a 2nd card.
        for (int i = 0; i < 2; i++) {
            for (TableSeat seat : activeSeats) {
                // Note: Ensure your domain model's 'setHoleCards' method *adds* the card to a list,
                // rather than overwriting the first card dealt!
                seat.setHoleCards(deck.getNextCard());
            }
        }

        // 6. INITIALIZE PRE-FLOP
        currentState = GameState.PRE_FLOP_BETTING;
        bettingPot.collectBlinds(tableManager);

        // 7. BROADCAST & PROMPT
        broadcaster.broadcastGameState(currentState);
        broadcastBlindActions();
        promptNextPlayer();
    }

    private boolean isHandInProgress() {
        return currentState == GameState.PRE_FLOP_BETTING
                || currentState == GameState.FLOP_BETTING
                || currentState == GameState.TURN_BETTING
                || currentState == GameState.RIVER_BETTING;
    }

    private List<TableSeat> getActiveSeats() {
        // Filters out null chairs AND chairs that have no player sitting in them
        return tableManager.getSeats().stream()
                .filter(seat -> seat != null && seat.getProfile() != null)
                .toList();
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
            System.out.println("[Engine] BOT: " + playerId + " successfully joined the table.");

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
             if (tableManager.getActivePlayerCount() >= 6 && currentState == GameState.WAITING_FOR_PLAYERS) {
                 startNewHand();
             }
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

        if (isHandInProgress()) {
            TableSeat actor = tableManager.getCurrentPlayer();
            if (actor != null) {
                int amountToCall = bettingPot.getHighestBet() - actor.getCurrentRoundBet();

                broadcaster.broadcastTurnPrompt(actor, amountToCall);
            }
        }
    }

    @Override
    public void StartHand() {
        startNewHand();
    }


    private TableSeat validateAndGetActor(String username) {
        TableSeat actor = tableManager.getCurrentPlayer();
        if (actor == null || !actor.getUsername().equals(username)) {
            System.err.println("[Engine Warning] Ignored out-of-turn action from: " + username);
            return null;
        }
        return actor;
    }

    public void leavePlayer(String identifier) {
        // 1. Use the new bulletproof lookup method we just added
        TableSeat seat = tableManager.findByIdOrUsername(identifier).orElse(null);

        // 2. THE FIX: If the seat is null, ABORT immediately!
        // Do not attempt to track bankrolls, do not pass go. Just stop.
        if (seat == null) {
            System.err.println("[Engine] Ignored leave request: Player " + identifier + " is not at the table.");
            return;
        }

        System.out.println("[Engine] Successfully found player " + identifier + " in Seat " + seat.getSeatIndex() + ". Removing...");

        // 3. Now it is safe to touch the seat properties!
        if (seat.isBankrollTracked()) {
            // Handle your bankroll saving logic here...
        }

        if (isBetweenHands()) {
            updateTrackedBankrolls();
            tableManager.removeById(identifier); // Make sure this uses the new logic too!
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

    public void configureTableBuyIn(int buyIn) {
        if (!isBetweenHands() || tableManager.size() > 0) {
            return;
        }

        this.tableBuyIn = Math.max(1, buyIn);
        bettingPot.setSmallBlindAmount(Math.max(1, this.tableBuyIn / 50));
    }

    public TableSeat sitPlayerDown(String id, int chips, int idx) {
        return tableManager.findByUsername(id).orElseGet(() -> {

            // 1. Determine the correct physical chair
            int seatIndex = idx >= 0 ? idx : findFirstOpenSeatIndex();
            if (seatIndex < 0 || seatIndex >= tableManager.size()) { // Safer bounds check
                System.err.println("[Engine] Cannot sit player " + id + ": Table is full or index invalid.");
                return null;
            }

            // 2. Load and validate their profile
            PlayerProfileDTO profile = loadSeatProfile(id, chips);
            if (profile == null) {
                System.err.println("[Engine] Cannot sit player " + id + ": Profile failed to load.");
                return null;
            }

            // 3. Construct the seat and handle bankroll deductions
            TableSeat seat = new TableSeat(profile, chips);
            if (playerRepository != null && profile.email() != null) {
                int bankrollBase = profile.totalBankroll() - chips;
                seat.trackBankrollFromBase(bankrollBase);
                saveBankroll(profile, bankrollBase);
            }

            seat.setSeatIndex(seatIndex);

            // 4. THE FIX: Lock them into the exact pre-filled array slot!
            // Do NOT use tableManager.addSeat(seat) which appends to the end of the list.
            tableManager.getSeats().set(seatIndex, seat);

            // 5. Broadcast the new layout to everyone else
            broadcaster.broadcastSeatOccupied(seat, currentHandId);

            System.out.println("[Engine] Successfully seated " + id + " at index " + seatIndex);
            return seat;
        });
    }

    public TableSeat sitPlayerDown(String id) {
        return sitPlayerDown(id, tableBuyIn, -1);
    }

    public boolean hasPlayer(String username) {
        return tableManager.findByUsername(username).isPresent();
    }

    public boolean hasSeatAvailable() {
        return findFirstOpenSeatIndex() >= 0;
    }

    public boolean isBetweenHands() {
        return currentState == GameState.WAITING_FOR_PLAYERS || currentState == GameState.HAND_OVER;
    }

    public void startHandIfReady() {
        if (currentState == GameState.HAND_OVER && nextHandScheduled) {
            return;
        }

        if (tableManager.size() >= 2 && isBetweenHands()) {
            startNewHand();
        }
    }

    public List<String> getSeatedUsernames() {
        return tableManager.getSeats().stream().map(TableSeat::getUsername).toList();
    }

    public List<HandParticipantDTO> getTableParticipants() {
        return getTableParticipantsForViewer(null, false);
    }

    public List<HandParticipantDTO> getTableParticipantsForViewer(String viewerUsername, boolean revealAllCards) {
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

    private void scheduleNextHand() {
        if (nextHandScheduled || tableManager.size() < 2) return;
        nextHandScheduled = true;

        scheduler.schedule(this::startScheduledHandIfReady, 8, TimeUnit.SECONDS);
    }

    private void startScheduledHandIfReady() {
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

    private void pauseIfNoHumansLeft() {
        boolean humanFound = false;

        // 1. Scan the table for real humans
        for (TableSeat seat : tableManager.getSeats()) {
            if (seat != null && seat.getProfile() != null) {
                String username = seat.getProfile().username();
                // If the username does NOT start with "Bot_", a human is here!
                if (username != null && !username.startsWith("Bot_")) {
                    humanFound = true;
                    break;
                }
            }
        }

        // 2. If no humans are found, shut down the bot party
        if (!humanFound) {
            System.out.println("[Engine] Last human left. Evicting bots and pausing game...");

            for (int i = 0; i < tableManager.getSeats().size(); i++) {
                TableSeat seat = tableManager.getSeats().get(i);
                if (seat != null && seat.getProfile() != null) {
                    if (seat.getProfile().username().startsWith("Bot_")) {
                        // Empty the seat to kick the bot
                        tableManager.getSeats().set(i, null);
                    }
                }
            }

            // Force the game state to stop dealing hands
            currentState = GameState.WAITING_FOR_PLAYERS;
            bettingPot.clearPot();
        }
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
