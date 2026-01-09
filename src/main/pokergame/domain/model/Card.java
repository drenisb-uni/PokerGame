package main.pokergame.domain.model;

public class Card {
    private int value;
    private String suit;
    private String color;

    public Card(int value, String suit) {
        this.value = value;
        this.suit = suit;
        if (this.suit.equals("Clubs") || this.suit.equals("Spades")) {
            this.color = "Black";
        }
        if (this.suit.equals("Hearts") || this.suit.equals("Diamonds")) {
            this.color = "Red";
        }
    }

    public int getValue() {
        return this.value;
    }

    public String getSuit() {
        return this.suit;
    }

    public String getColor() {
        return this.color;
    }

    public String toImageString() {
        return (this.value + "-" + this.suit.substring(0, 1).toUpperCase() + ".png");
    }
}
