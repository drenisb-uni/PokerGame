package pokergame.server.engine;

import pokergame.domain.model.*;
import pokergame.engine.GameState;
import pokergame.engine.IGameEventListener;
import pokergame.engine.IPublicActionAPI;
import pokergame.server.domain.model.Deck;
import pokergame.server.domain.model.TableSeat;
import pokergame.server.domain.repository.IPlayerRepository;
import pokergame.domain.rules.*;
import pokergame.server.domain.rules.HandRanker;

import java.util.*;

public class PokerGameEngine implements IPublicActionAPI {
    private final IPlayerRepository playerRepository;
    private final TableManager tableManager = new TableManager();
    private final BettingPot bettingPot = new BettingPot();
    private final GameEventBroadcaster broadcaster = new GameEventBroadcaster();

    private final Deck deck = new Deck();
    private final List<Card> communityCards = new ArrayList<>();
    private GameState currentState = GameState.WAITING_FOR_PLAYERS;
    private String currentHandId;

    public PokerGameEngine(IPlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void startNewHand() {
        if (tableManager.size() < 2) return;

        currentHandId = UUID.randomUUID().toString();
        communityCards.clear();
        bettingPot.clearPot();

        tableManager.getSeats().forEach(seat -> {
            seat.setFolded(false);
            seat.setRoundBet(0);
            seat.clearCards();
        });

        deck.shuffleDeck();
        // Deal cards
        for (int i = 0; i < 2; i++) {
            for (TableSeat seat : tableManager.getSeats()) {
                seat.setHoleCards(deck.getNextCard());
            }
        }

        bettingPot.collectBlinds(tableManager);
        currentState = GameState.PRE_FLOP_BETTING;
        broadcaster.broadcastGameState(currentState);
        promptNextPlayer();
    }

    public void executePlayerAction(String username, String actionType, int amount) {
        TableSeat actor = tableManager.getCurrentPlayer();
        if (!actor.getUsername().equals(username)) return;

        switch (actionType.toUpperCase()) {
            case "FOLD" -> Fold(username);
            case "CALL" -> Call(username);
            case "RAISE" -> Raise(username, amount);
        }

        broadcaster.broadcastAction(actor, actionType, amount, currentState, currentHandId);
        advanceTurn();
    }

    @Override
    public void Fold(String actorUsername) {
        tableManager.findByUsername(actorUsername).ifPresent(bettingPot::handleFold);
        advanceTurn();
    }

    @Override
    public void Call(String actorUsername) {
        tableManager.findByUsername(actorUsername).ifPresent(bettingPot::handleCall);
        advanceTurn();
    }

    @Override
    public void Raise(String actorUsername, int amount) {
        tableManager.findByUsername(actorUsername).ifPresent(seat ->
                bettingPot.handleRaise(seat, amount, tableManager.getActivePlayerCount())
        );
        advanceTurn();
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
        broadcaster.broadcastGameState(currentState);
        tableManager.rotateDealer();
    }

    // Pass-through getters for tests
    public int getPotSize() { return bettingPot.getPotSize(); }
    public int getHighestCurrentBet() { return bettingPot.getHighestBet(); }
    public TableSeat getPlayerByUsername(String name) { return tableManager.findByUsername(name).orElse(null); }
    public GameState getCurrentState() { return currentState; }
    public void addObserver(IGameEventListener obs) { broadcaster.addObserver(obs); }
    public void sitPlayerDown(String id, int chips, int idx) {
        tableManager.addSeat(new TableSeat(new pokergame.domain.dto.PlayerProfileDTO(id, id, null, null, chips, null), chips));
    }
}