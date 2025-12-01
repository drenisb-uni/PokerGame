import java.util.ArrayList;

public class StandardDeck {
    private ArrayList<StandardCard> deck = new ArrayList<StandardCard>();

    public StandardDeck() {
        reset();
        shuffleDeck();
    }

    public void reset() {
        this.deck.clear();

        for (int i = 2; i < 15; i++) {
            deck.add(new StandardCard(i, "Hearts"));
        }
        for (int i = 2; i < 15; i++) {
            deck.add(new StandardCard(i, "Diamonds"));
        }
        for (int i = 2; i < 15; i++) {
            deck.add(new StandardCard(i, "Spades"));
        }
        for (int i = 2; i < 15; i++) {
            deck.add(new StandardCard(i, "Clubs"));
        }
    }

    public void shuffleDeck() {
        ArrayList<StandardCard> tempDeck = new ArrayList<StandardCard>();
        while (!deck.isEmpty()) {
            int randomNum = (int) (Math.random() * 100) % this.deck.size();
            tempDeck.add(this.deck.remove(randomNum));
        }
        this.deck = tempDeck;
    }

    public StandardCard getNextCard() {
        return this.deck.removeLast();
    }

    public int getRemainingCards() {
        return this.deck.size();
    }
}
