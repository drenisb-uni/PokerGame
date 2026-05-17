package pokergame.engine;

import pokergame.GameContext;
import pokergame.domain.dto.HandActionDTO;
import pokergame.domain.dto.HandParticipantDTO;
import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.model.Card;
import pokergame.domain.model.Deck;
import pokergame.domain.model.TableSeat;
import pokergame.domain.repository.IPlayerRepository;
import pokergame.domain.rules.HandRanker;
import pokergame.domain.rules.HandResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PokerGameEngine {
    private IPlayerRepository playerRepository;
    private ArrayList<IGameEventListener> observers = new ArrayList<>();

    private GameState currentState;
    private final List<TableSeat> tableSeats = new ArrayList<>();
    private final Deck deck;
    private final List<Card> communityCards = new ArrayList<>();

    // Betting & Game Tracking
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;
    private int highestBetThisRound = 0;
    private int potSize = 0;
    private int smallBlindAmount = 10; // Default small blind
    private int playersToAct = 0;
    private int actionSequenceCounter;
    private String currentHandId;

    public PokerGameEngine(IPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        this.deck = new Deck();
        this.currentState = GameState.WAITING_FOR_PLAYERS;
    }

    // --- LOBBY & SEATING ---

    public boolean joinTable(String username, int buyAmount) {
        PlayerProfileDTO user = playerRepository.findProfileByUsername(username);
        if (user == null) throw new IllegalArgumentException("User not found!");
        if (user.totalBankroll() < buyAmount) return false;

        TableSeat newSeat = new TableSeat(user, buyAmount);
        tableSeats.add(newSeat);
        notifySeatOccupied(newSeat);
        return true;
    }

    public void fillTableSeats(int buyAmount) {
        tableSeats.clear();
        TableSeat playerSeat = new TableSeat(GameContext.getPlayerProfile(), buyAmount);
        tableSeats.add(playerSeat);
        for (int i = 1; i < 6; i++) {
            PlayerProfileDTO user = new PlayerProfileDTO("u" + i, i + " guy", null, null, 1000, null);
            TableSeat mockSeat = new TableSeat(user, buyAmount);
            tableSeats.add(mockSeat);
            notifySeatOccupied(mockSeat);
        }
        notifySeatOccupied(playerSeat);
    }

    // --- STATE MACHINE & GAME LOOP ---

    public void startNewHand() {
        System.out.println("Starting New Hand...");
        if (tableSeats.size() < 2) return;

        resetForNewHand();
        deck.shuffleDeck();
        dealHoleCards();
        handleBlinds();

        currentState = GameState.PRE_FLOP_BETTING;
        notifyGameStateChanged(currentState);
        promptNextPlayer();
    }

    private void resetForNewHand() {
        communityCards.clear();
        potSize = 0;
        for (TableSeat seat : tableSeats) {
            seat.setFolded(false);
            seat.setRoundBet(0);
            seat.clearCards();
        }
    }

    private void dealHoleCards() {
        // Deal 2 cards to each active player
        for (int i = 0; i < 2; i++) {
            for (TableSeat seat : tableSeats) {
                seat.setHoleCards(deck.getNextCard());
            }
        }
    }

    private void handleBlinds() {
        int sbIndex = (dealerIndex + 1) % tableSeats.size();
        int bbIndex = (dealerIndex + 2) % tableSeats.size();

        // Small Blind
        TableSeat sbSeat = tableSeats.get(sbIndex);
        sbSeat.bet(smallBlindAmount);
        sbSeat.setRoundBet(smallBlindAmount);

        // Big Blind
        TableSeat bbSeat = tableSeats.get(bbIndex);
        int bigBlindAmount = smallBlindAmount * 2;
        bbSeat.bet(bigBlindAmount);
        bbSeat.setRoundBet(bigBlindAmount);

        potSize += (smallBlindAmount + bigBlindAmount);
        highestBetThisRound = bigBlindAmount;

        // Pre-flop starts with the player after the Big Blind (Under the Gun)
        currentPlayerIndex = (bbIndex + 1) % tableSeats.size();
        playersToAct = getActivePlayerCount();
    }

    public void executePlayerAction(String username, String actionType, int amount) {
        TableSeat actor = tableSeats.get(currentPlayerIndex);
        if (!actor.getUsername().equals(username)) return;

        switch (actionType.toUpperCase()) {
            case "FOLD" -> handleFold(actor);
            case "CALL" -> handleCall(actor); // Check is treated as a Call of 0
            case "RAISE" -> handleRaise(actor, amount);
        }

        notifyPlayerAction(actor, actionType, amount);
        playersToAct--;
        advanceTurn();
    }

    private void advanceTurn() {
        if (getActivePlayerCount() <= 1) {
            handleEarlyWin(); // Everyone else folded
            return;
        }

        if (isBettingRoundComplete()) {
            advanceGameStage();
        } else {
            moveToNextActivePlayer();
            promptNextPlayer();
        }
    }

    private void advanceGameStage() {
        resetBettingForNextRound();

        switch (currentState) {
            case PRE_FLOP_BETTING -> {
                currentState = GameState.FLOP_DEALING;
                dealCommunityCards(3);
                currentState = GameState.FLOP_BETTING;
            }
            case FLOP_BETTING -> {
                currentState = GameState.TURN_DEALING;
                dealCommunityCards(1);
                currentState = GameState.TURN_BETTING;
            }
            case TURN_BETTING -> {
                currentState = GameState.RIVER_DEALING;
                dealCommunityCards(1);
                currentState = GameState.RIVER_BETTING;
            }
            case RIVER_BETTING -> {
                currentState = GameState.SHOWDOWN;
                evaluateShowdown();
                return;
            }
        }

        notifyGameStateChanged(currentState);

        // Post-flop betting starts with the first active player after the dealer
        currentPlayerIndex = dealerIndex;
        moveToNextActivePlayer();
        playersToAct = getActivePlayerCount();
        promptNextPlayer();
    }

    // --- BETTING LOGIC ---

    private void handleFold(TableSeat actor) {
        actor.setFolded(true);
    }

    private void handleCall(TableSeat actor) {
        int amountToCall = highestBetThisRound - actor.getCurrentRoundBet();
        // Handle all-in logic here if amountToCall > actor.getChips()
        actor.bet(amountToCall);
        actor.setRoundBet(highestBetThisRound);
        potSize += amountToCall;
    }

    private void handleRaise(TableSeat actor, int raiseTotalAmount) {
        int additionalAmount = raiseTotalAmount - actor.getCurrentRoundBet();
        actor.bet(additionalAmount);
        actor.setRoundBet(raiseTotalAmount);
        potSize += additionalAmount;
        highestBetThisRound = raiseTotalAmount;

        // Reset players to act because everyone else now has to respond to the new raise
        playersToAct = getActivePlayerCount();
    }

    private void resetBettingForNextRound() {
        highestBetThisRound = 0;
        for (TableSeat seat : tableSeats) {
            seat.setRoundBet(0);
        }
    }

    private boolean isBettingRoundComplete() {
        if (playersToAct > 0) return false;

        // Verify all active players have matched the highest bet
        for (TableSeat seat : tableSeats) {
            if (!seat.isFolded() && seat.getCurrentRoundBet() != highestBetThisRound) {
                return false;
            }
        }
        return true;
    }

    private void moveToNextActivePlayer() {
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % tableSeats.size();
        } while (tableSeats.get(currentPlayerIndex).isFolded());
    }

    private int getActivePlayerCount() {
        int count = 0;
        for (TableSeat seat : tableSeats) {
            if (!seat.isFolded()) count++;
        }
        return count;
    }

    private void promptNextPlayer() {
        TableSeat actor = tableSeats.get(currentPlayerIndex);
        int amountToCall = highestBetThisRound - actor.getCurrentRoundBet();
        notifyPlayerTurn(actor, amountToCall);
    }

    // --- DEALING & WINNING ---

    private void dealCommunityCards(int count) {
        List<Card> newCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Card c = deck.getNextCard();
            communityCards.add(c);
            newCards.add(c);
        }
        notifyCardsDealt(newCards);
    }

    private void handleEarlyWin() {
        TableSeat winner = null;
        for (TableSeat seat : tableSeats) {
            if (!seat.isFolded()) winner = seat;
        }
        if (winner != null) {
            winner.addChipsOnTable(potSize);
            notifyHandResult(List.of(winner), null, potSize);
        }
        endHand();
    }

    private void evaluateShowdown() {
        HandRanker ranker = new HandRanker();

        // HandRanker expects an array for community cards, so we convert the list
        Card[] commCardsArray = communityCards.toArray(new Card[0]);

        // 1. Get all players who haven't folded
        List<TableSeat> activePlayers = tableSeats.stream()
                .filter(seat -> !seat.isFolded())
                .toList();

        if (activePlayers.isEmpty()) {
            endHand();
            return;
        }

        // 2. Evaluate each active player's hand and store the results
        Map<TableSeat, HandResult> playerResults = new HashMap<>();
        for (TableSeat seat : activePlayers) {
            HandResult result = ranker.evaluate(seat.getHoleCards(), commCardsArray);
            playerResults.put(seat, result);
        }

        // 3. Find the best possible hand at the table using your Comparable implementation
        HandResult bestResult = playerResults.values().stream()
                .max(HandResult::compareTo)
                .orElseThrow(); // Should never throw since activePlayers isn't empty

        // 4. Find all players who hold that best hand (this handles Split Pots naturally!)
        List<TableSeat> winners = activePlayers.stream()
                .filter(seat -> playerResults.get(seat).compareTo(bestResult) == 0)
                .toList();

        // 5. Divide the pot and award chips
        int splitPot = potSize / winners.size();
        for (TableSeat winner : winners) {
            winner.addChipsOnTable(splitPot);
        }

        // 6. Notify the UI
        notifyHandResult(winners, bestResult, potSize);

        endHand();
    }

    private void endHand() {
        currentState = GameState.HAND_OVER;
        notifyGameStateChanged(currentState);

        // Rotate dealer
        dealerIndex = (dealerIndex + 1) % tableSeats.size();
    }

    public int getPotSize(){
        return this.potSize;
    }

    // --- OBSERVER NOTIFICATIONS ---

    public void addObserver(IGameEventListener observer) { this.observers.add(observer); }// Inside poker-server module

    private void notifySeatOccupied(TableSeat newSeat) {
        HandParticipantDTO participantDto = new HandParticipantDTO(
                this.currentHandId == null ? "WAITING_FOR_HAND" : this.currentHandId,
                newSeat.getUsername(),
                newSeat.getSeatIndex(),
                "HIDDEN",
                newSeat.getChipsOnTable(),
                newSeat.getChipsOnTable(),
                0,
                false
        );

        observers.forEach(o -> o.onNewSeatOccupied(participantDto));
    }

    private void notifyPlayerAction(TableSeat actor, String actionType, int amount) {
        HandActionDTO actionDto = new HandActionDTO(
                0,
                this.currentHandId,
                actor.getUsername(),
                this.currentState.name(),
                this.actionSequenceCounter++,
                actionType,
                amount
        );
        observers.forEach(o -> o.onPlayerAction(actionDto));
    }

    private void notifyPlayerTurn(TableSeat actor, int amount) {
        observers.forEach(o -> o.onPlayerTurn(actor.getUsername(), amount));
    }
    private void notifyGameStateChanged(GameState state) { observers.forEach(o -> o.onGameStateChanged(state)); }
    private void notifyCardsDealt(List<Card> cards) { observers.forEach(o -> o.onCommunityCardsDealt(cards)); }
    private void notifyHandResult(List<TableSeat> winners, HandResult winnerHand, int potSize) {
        // 1. Convert the List<TableSeat> into a List<String> of usernames
        List<String> winnerUsernames = winners.stream()
                .map(TableSeat::getUsername)
                .toList();

        // 2. Broadcast the Strings, keeping the TableSeat objects safely on the server
        observers.forEach(o -> o.onHandResult(winnerUsernames, winnerHand, potSize));
    }
}