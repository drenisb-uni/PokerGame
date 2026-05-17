package pokergame.domain.model;

import pokergame.domain.dto.PlayerProfileDTO;

import java.util.ArrayList;
import java.util.List;

public class TableSeat {
    private final PlayerProfileDTO profile;
    private int chipsOnTable;
    private int seatIndex;
    private List<Card> holeCards;
    private boolean isFolded;
    private int currentRoundBet;

    public TableSeat(PlayerProfileDTO profile, int buyInAmount) {
        this.profile = profile;
        this.chipsOnTable = buyInAmount;
        this.holeCards = new ArrayList<>();
        this.isFolded = false;
    }

    public void bet(int amount) {
        this.chipsOnTable -= amount;
        this.currentRoundBet += amount;
    }

    public String getUsername() { return this.profile.username(); }

    public int getChipsOnTable() { return this.chipsOnTable; }

    public void addChipsOnTable(int chipsOnTable) {
        this.chipsOnTable += chipsOnTable;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public List<Card> getHoleCards() { return this.holeCards; }

    public void setHoleCards(Card cards){ holeCards.add(cards); }

    public void setFolded(boolean folded) { this.isFolded = folded; }

    public boolean isFolded() { return this.isFolded; }

    public void setRoundBet(int roundBet) { this.currentRoundBet = roundBet; }

    public int getCurrentRoundBet() { return this.currentRoundBet; }

    public void clearCards(){ holeCards.clear(); }
}
