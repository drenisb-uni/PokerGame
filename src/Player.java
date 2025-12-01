import java.util.Arrays;

public class Player {
    private String name;
    private int balance;
    private StandardCard[] holeCards = new StandardCard[2];
    private boolean isInGame;

    public Player(String name, int balance, StandardCard[] holeCards) {
        this.name = name;
        this.balance = balance;
        this.holeCards = holeCards;
        this.isInGame = true;
    }

    public String getName() {
        return name;
    }

    public int getBalance() {
        return balance;
    }

    public void addToBalance(int addAmount) {
        balance += addAmount;
    }

    public void reduceToBalance(int reduceAmount) {
        balance -= reduceAmount;
    }

    public StandardCard[] getHoleCards() {
        return this.holeCards;
    }

    public boolean isInGame() {
        return this.isInGame;
    }

    public void setIsInGame(boolean isInGame) {
        this.isInGame = isInGame;
    }

    public void resetHoleCards(StandardCard[] holeCards) {
        this.holeCards = holeCards;
    }

    public String toString() {
        return ("Player: " + this.name + " Balance: " + this.balance + "\n"
        + "Hole Cards: " + Arrays.toString(holeCards) + "\n"
        + "Is In Game: " + this.isInGame);
    }
}
