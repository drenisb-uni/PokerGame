package main.pokergame.view;

import main.pokergame.domain.model.Card;
import main.pokergame.domain.model.PlayerProfile;
import main.pokergame.domain.model.TableSeat;
import main.pokergame.domain.rules.HandResult;
import main.pokergame.engine.GameEventListener;
import main.pokergame.engine.GameState;
import main.pokergame.engine.PokerGameEngine;

import java.util.List;

public class ConsoleDebbuger implements GameEventListener {
    private PokerGameEngine engine;
    public ConsoleDebbuger(PokerGameEngine engine) {
        this.engine = engine;
    }

    @Override
    public void onNewSeatOccupied(TableSeat tableSeat) {
        System.out.println("DEBUG: player has sat on table seat " + tableSeat);
    }

    @Override
    public void onGameStateChanged(GameState newState) {
        System.out.println("DEBUG: State changed to " + newState);
    }

    @Override
    public void onCommunityCardsDealt(List<Card> cards) {
        System.out.println("DEBUG: Community cards dealt: " + cards);
    }

    @Override
    public void onPlayerTurn(TableSeat activePlayer, int amountToCall) {
        System.out.println("DEBUG: The player _" + activePlayer + " has to call _" + amountToCall);
    }

    @Override
    public void onPlayerAction(TableSeat player, String actionType, int amount) {
        System.out.println("DEBUG: The player _" + player + " acted _" + actionType + " amount _" + amount);
    }

    @Override
    public void onHandResult(List<TableSeat> winners, HandResult winningHand, int potSize) {
        System.out.println("DEBUG: The player _" + winners + " with hand _" + winningHand + " won _" + potSize);
    }
}
