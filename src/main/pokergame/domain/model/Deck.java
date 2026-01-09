package main.pokergame.domain.model;

import java.util.ArrayList;

public class Deck {
    private ArrayList<Card> deck = new ArrayList<Card>();

    public Deck() {
        reset();
        shuffleDeck();
    }

    public void reset() {
        this.deck.clear();

        for (int i = 2; i < 15; i++) {
            deck.add(new Card(i, "Hearts"));
        }
        for (int i = 2; i < 15; i++) {
            deck.add(new Card(i, "Diamonds"));
        }
        for (int i = 2; i < 15; i++) {
            deck.add(new Card(i, "Spades"));
        }
        for (int i = 2; i < 15; i++) {
            deck.add(new Card(i, "Clubs"));
        }
    }

    public void shuffleDeck() {
        ArrayList<Card> tempDeck = new ArrayList<Card>();
        while (!deck.isEmpty()) {
            int randomNum = (int) (Math.random() * 100) % this.deck.size();
            tempDeck.add(this.deck.remove(randomNum));
        }
        this.deck = tempDeck;
    }

    public Card getNextCard() {
        return this.deck.removeLast();
    }

    public int getRemainingCards() {
        return this.deck.size();
    }
}
