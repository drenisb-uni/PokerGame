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

    public String getUsername() { return this.profile.getUsername(); }
    public int getChipsOnTable() { return this.chipsOnTable; }
    public List<Card> getHoleCards() { return this.holeCards; }
    public boolean isFolded() { return this.isFolded; }
    private int getCurrentRoundBet() { return this.currentRoundBet; }
}
