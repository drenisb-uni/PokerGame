package main.pokergame.engine;

import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.Deck;
import main.pokergame.domain.model.PlayerProfile;
import main.pokergame.domain.model.TableSeat;
import main.pokergame.domain.repository.PlayerRepository;
import main.pokergame.domain.rules.HandResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PokerGameEngine {
    private PlayerRepository playerRepository;
    private ArrayList<GameEventListener> observers = new ArrayList<>();

    private GameState currentState;
    private final List<TableSeat> tableSeats =  new ArrayList<>();
    private final Deck deck;
    private final List<Card> communityCards =  new ArrayList<>();

    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;

    public PokerGameEngine(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
        this.deck = new Deck();
        this.currentState = GameState.WAITING_FOR_PLAYERS;
    }

    public void save(PlayerProfile profile) {
        playerRepository.saveProfile(profile);
    }

    public boolean joinTable(String username, int buyAmount) {
        PlayerProfile user = playerRepository.findProfileByUsername(username);

        if (user == null)
            throw new IllegalArgumentException("User not found!");
        if (user.getTotalBankroll() < buyAmount)
            return false;

        user.setTotalBankroll(user.getTotalBankroll() - buyAmount);
        playerRepository.saveProfile(user);

        TableSeat newSeat = new TableSeat(user, buyAmount);
        tableSeats.add(newSeat);

        notifySeatOccupied(newSeat);
        return true;
    }

    public void startNewHand() {
        if (tableSeats.size() < 2) return;

        deck.shuffleDeck();

        //resetRoundState();

        //handleBlinds();

        //dealHoleCards();

        currentState = GameState.PRE_FLOP_BETTING;
        notifyGameStateChanged(currentState);
        promptNextPlayer();
    }

    public void executePlayerAction(String username, String actionType, int amount) {
        TableSeat actor = tableSeats.get(currentPlayerIndex);
        if (!actor.getUsername().equals(username))
            return;

        switch (actionType) {
            case "FOLD" -> handleFold(actor);
            case "CALL" -> handleCall(actor);
            case "RAISE" -> handleRaise(actor, amount);
        }

        notifyPlayerAction(actor, actionType, amount);
        advanceTurn();
    }

    private void advanceTurn() {
        if (isBettingRoundComplete()) {
            advanceGameStage();
        } else {
            currentPlayerIndex = (currentPlayerIndex + 1) % tableSeats.size();
            while (tableSeats.get(currentPlayerIndex).isFolded()) {
                currentPlayerIndex = (currentPlayerIndex + 1) % tableSeats.size();
            }
            promptNextPlayer();
        }
    }

    private void advanceGameStage() {
        resetBettingForNextRound();

        switch (currentState) {
            case PRE_FLOP_BETTING -> {
                currentState = GameState.FLOP_DEALING;
            }
            case FLOP_DEALING -> {
                currentState = GameState.FLOP_BETTING;
            }
            case FLOP_BETTING -> {
                currentState = GameState.TURN_DEALING;
            }
            case TURN_DEALING -> {
                currentState = GameState.TURN_DEALING;
            }
            case TURN_BETTING -> {
                currentState = GameState.RIVER_DEALING;
            }
            case RIVER_DEALING -> {
                currentState = GameState.RIVER_BETTING;
            }
            case RIVER_BETTING -> {
                currentState = GameState.SHOWDOWN;
            }
            case SHOWDOWN -> {
                currentState = GameState.HAND_OVER;
            }
        }

        notifyGameStateChanged(currentState);
        currentPlayerIndex = getNextActiveSeatIndex(dealerIndex);
        promptNextPlayer();
    }

    private int getNextActiveSeatIndex(int dealerIndex) {
        if (dealerIndex == tableSeats.size() - 1)
            return dealerIndex + 1;
        else
            return 0;
    }

    //call fold raise

    private void handleFold(TableSeat actor) {
    }

    private void handleCall(TableSeat actor) {
    }

    private void handleRaise(TableSeat actor, int amount) {
    }

    //betting round

    private void resetBettingForNextRound() {
    }

    private boolean isBettingRoundComplete() {
        return currentState != GameState.PRE_FLOP_BETTING;
    }

    //Observer pattern functions

    public void addObserver(GameEventListener observer){
        this.observers.add(observer);
    }

    private void promptNextPlayer(){
        TableSeat actor = tableSeats.get(currentPlayerIndex);
        int amount = 0; //match betting amount
        notifyPlayerTurn(actor, amount);
    }

    private void notifySeatOccupied(TableSeat newSeat) {
        for (GameEventListener obs : observers)
            obs.onNewSeatOccupied(newSeat);
    }

    private void notifyGameStateChanged(GameState currentState) {
        for (GameEventListener obs : observers)
            obs.onGameStateChanged(currentState);
    }

    private void notifyCardsDealt(List<Card> cards) {
        for (GameEventListener obs : observers)
            obs.onCommunityCardsDealt(cards);
    }

    private void notifyPlayerTurn(TableSeat actor, int amount) {
        for (GameEventListener obs : observers)
            obs.onPlayerTurn(actor, amount);
    }
    private void notifyPlayerAction(TableSeat actor, String actionType, int amount) {
        for (GameEventListener obs : observers)
            obs.onPlayerAction(actor, actionType, amount);
    }
    private void notifyHandResult(List<TableSeat> winners, HandResult winnerHand, int potSize) {
        for (GameEventListener obs : observers)
            obs.onHandResult(winners, winnerHand, potSize);
    }





}