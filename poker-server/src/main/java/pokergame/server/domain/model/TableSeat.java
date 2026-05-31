package pokergame.server.domain.model;

import pokergame.domain.dto.PlayerProfileDTO;
import pokergame.domain.model.Card;

import java.util.ArrayList;
import java.util.List;

public class TableSeat {
    private final PlayerProfileDTO profile;
    private int chipsOnTable;
    private int seatIndex;
    private List<Card> holeCards;
    private boolean isFolded;
    private int currentRoundBet;
    private int bankrollBase;
    private boolean bankrollTracked;
    private boolean persistenceTracked;

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

    public PlayerProfileDTO getProfile() { return this.profile; }

    public int getChipsOnTable() { return this.chipsOnTable; }

    public void addChipsOnTable(int chipsOnTable) {
        this.chipsOnTable += chipsOnTable;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public void setSeatIndex(int seatIndex) {
        this.seatIndex = seatIndex;
    }

    public List<Card> getHoleCards() { return this.holeCards; }

    public void setHoleCards(Card cards){ holeCards.add(cards); }

    public void setFolded(boolean folded) { this.isFolded = folded; }

    public boolean isFolded() { return this.isFolded; }

    public void setRoundBet(int roundBet) { this.currentRoundBet = roundBet; }

    public int getCurrentRoundBet() { return this.currentRoundBet; }

    public void clearCards(){ holeCards.clear(); }

    public void trackBankrollFromBase(int bankrollBase) {
        this.bankrollBase = bankrollBase;
        this.bankrollTracked = true;
    }

    public void trackPersistence() {
        this.persistenceTracked = true;
    }

    public int getBankrollBase() {
        return bankrollBase;
    }

    public boolean isBankrollTracked() {
        return bankrollTracked;
    }

    public boolean isPersistenceTracked() {
        return persistenceTracked;
    }
}
