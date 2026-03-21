package pokergame.domain.model;

import pokergame.domain.dto.PlayerProfileDTO;

import java.util.ArrayList;
import java.util.List;

public class TableSeat {
    private final PlayerProfileDTO profile;
    private int chipsOnTable;
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
    public List<Card> getHoleCards() { return this.holeCards; }
    public boolean isFolded() { return this.isFolded; }
    private int getCurrentRoundBet() { return this.currentRoundBet; }
}
