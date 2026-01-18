package main.pokergame.domain.model;

import java.util.ArrayList;
import java.util.List;

public class TableSeat {
    private final PlayerProfile profile;
    private int chipsOnTable;
    private List<Card> holeCards;
    private boolean isFolded;
    private int currentRoundBet;

    public TableSeat(PlayerProfile profile, int buyInAmount) {
        this.profile = profile;
        this.chipsOnTable = buyInAmount;
        this.holeCards = new ArrayList<>();
        this.isFolded = false;
    }

    public void bet(int amount) {
        this.chipsOnTable -= amount;
        this.currentRoundBet += amount;
    }

    public int getChipsOnTable() { return chipsOnTable; }
    public List<Card> getHoleCards() { return holeCards; }
    public boolean isFolded() { return isFolded; }
    private int getCurrentRoundBet() { return currentRoundBet; }
}
